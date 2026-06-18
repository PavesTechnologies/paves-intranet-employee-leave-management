package com.paves.employee_leave_management.websockets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthChannelInterceptor.class);

    private final JwtDecoder jwtDecoder;

    public AuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // ── CONNECT ──────────────────────────────────────────────────────────
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);

            if (token == null) {
                throw new IllegalArgumentException("Missing JWT token in WebSocket CONNECT");
            }

            try {
                Jwt jwt = jwtDecoder.decode(token);

                // FIX F10: null-check the claim object BEFORE calling .toString().
                // Previously getClaim("user_id").toString() threw NPE when the
                // claim was absent, which was caught by the outer try-catch and
                // re-thrown as a confusing "Invalid JWT" message. The dead
                // if(userId == null) check below it was never reached.
                Object userIdClaim = jwt.getClaim("user_id");
                if (userIdClaim == null) {
                    throw new IllegalArgumentException("JWT missing required 'user_id' claim");
                }
                String userId = userIdClaim.toString();

                List<SimpleGrantedAuthority> authorities = extractAuthorities(jwt);

                Principal principal = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );

                accessor.setUser(principal);

                // FIX F13: replaced System.out.println with SLF4J.
                // println has no log-level control and breaks unified log
                // pipelines in containerised environments.
                log.info("WS AUTH SUCCESS userId={}", userId);

            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                // FIX F13: was System.err.println
                log.warn("WS AUTH FAILED: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid JWT: " + e.getMessage());
            }
        }

        // ── SUBSCRIBE ────────────────────────────────────────────────────────
        // FIX F1: previously only checked principal != null.
        // Any authenticated user could subscribe to /topic/manager/* (all
        // manager leave notifications) or /user/{otherId}/queue/* (another
        // employee's personal queue), giving full cross-tenant read access.
        //
        // Now enforces two rules:
        //   1. Personal queue subscriptions: the {userId} segment in the path
        //      must match the authenticated principal. Spring rewrites
        //      /user/queue/X to /user/{principal}/queue/X internally, so
        //      clients that use the canonical /user/queue/X form are always
        //      safe. Only explicit /user/{otherId}/queue/X attempts are blocked.
        //   2. Manager-only topics (/topic/manager/*, /topic/comp-off*) require
        //      ROLE_REPORTING_MANAGER, ROLE_HR, or ROLE_SUPER_ADMIN.
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal principal = accessor.getUser();
            if (principal == null) {
                throw new IllegalArgumentException("Unauthenticated subscription attempt blocked");
            }

            String dest = accessor.getDestination();
            String userId = principal.getName();

            if (dest != null && dest.startsWith("/user/")) {
                // /user/queue/X is the canonical client form — Spring rewrites
                // it internally to /user/{principal}/queue/X, so it is always
                // safe. Only block explicit /user/{otherId}/... attempts.
                if (!dest.startsWith("/user/queue/")) {
                    String afterUser = dest.substring("/user/".length());
                    int slash = afterUser.indexOf('/');
                    if (slash > 0) {
                        String targetUser = afterUser.substring(0, slash);
                        if (!targetUser.equals(userId)) {
                            log.warn("WS SUBSCRIBE BLOCKED userId={} attempted dest={}", userId, dest);
                            throw new IllegalArgumentException(
                                    "Not authorized to subscribe to " + dest);
                        }
                    }
                }
            }

            if (dest != null && (dest.startsWith("/topic/manager/") ||
                    dest.startsWith("/topic/comp-off"))) {
                boolean isPrivileged = principal instanceof UsernamePasswordAuthenticationToken t &&
                        t.getAuthorities().stream().anyMatch(a ->
                                a.getAuthority().startsWith("ROLE_REPORTING_MANAGER") ||
                                        a.getAuthority().startsWith("ROLE_HR") ||
                                        a.getAuthority().startsWith("ROLE_SUPER_ADMIN"));
                if (!isPrivileged) {
                    log.warn("WS SUBSCRIBE FORBIDDEN userId={} dest={}", userId, dest);
                    throw new IllegalArgumentException("Forbidden destination: " + dest);
                }
            }

            log.debug("WS SUBSCRIBE userId={} dest={}", userId, dest);
        }

        // ── SEND ─────────────────────────────────────────────────────────────
        // FIX F6: no SEND guard existed. An authenticated STOMP client could
        // send frames directly to any /topic/* or /queue/* broker destination,
        // bypassing all @MessageMapping controllers. With SimpleBroker these
        // frames are forwarded directly to subscribers — an attacker could
        // inject phantom leave notifications visible to all managers.
        //
        // Only /app/* sends (routed to @MessageMapping controllers) are
        // permitted. The server side sends to /topic/* and /queue/* directly
        // via SimpMessagingTemplate — those are server-initiated, not client
        // SEND frames, so this guard does not affect them.
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            String dest = accessor.getDestination();
            if (dest != null && !dest.startsWith("/app/")) {
                log.warn("WS SEND BLOCKED userId={} attempted dest={}",
                        accessor.getUser() != null ? accessor.getUser().getName() : "unknown",
                        dest);
                throw new IllegalArgumentException(
                        "Client SEND is restricted to /app/ destinations. Got: " + dest);
            }
        }

        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get("token") != null) {
            return (String) attrs.get("token");
        }
        return null;
    }

    private List<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(role -> authorities.add(
                    new SimpleGrantedAuthority("ROLE_" + role.replace(" ", "_").toUpperCase())
            ));
        }

        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null) {
            permissions.forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));
        }

        return authorities;
    }
}