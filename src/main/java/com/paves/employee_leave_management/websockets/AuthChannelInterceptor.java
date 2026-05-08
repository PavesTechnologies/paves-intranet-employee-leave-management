package com.paves.employee_leave_management.websockets;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
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

    private final JwtDecoder jwtDecoder;

    public AuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // 🔐 Authenticate on CONNECT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);

            if (token == null) {
                throw new IllegalArgumentException("Missing JWT token in WebSocket CONNECT");
            }

            try {
                Jwt jwt = jwtDecoder.decode(token);

                // ✅ Use user_id as the principal name
                // This MUST match what you pass to convertAndSendToUser()
                String userId = jwt.getClaim("user_id").toString();

                if (userId == null) {
                    throw new IllegalArgumentException("JWT missing 'user_id' claim");
                }

                List<SimpleGrantedAuthority> authorities = extractAuthorities(jwt);

                Principal principal = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );

                accessor.setUser(principal);
                System.out.println("✅ WS AUTH SUCCESS → userId: " + userId);

            } catch (Exception e) {
                System.err.println("❌ WS AUTH FAILED: " + e.getMessage());
                throw new IllegalArgumentException("Invalid JWT: " + e.getMessage());
            }
        }

        // 🔐 Guard SUBSCRIBE — user must be authenticated
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal principal = accessor.getUser();
            if (principal == null) {
                throw new IllegalArgumentException("Unauthenticated subscription attempt blocked");
            }
        }

        return message;
    }

    /**
     * Extract token from:
     * 1. Authorization STOMP header (primary)
     * 2. Session attributes set by HandshakeInterceptor (SockJS fallback)
     */
    private String resolveToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Fallback: token stored during HTTP handshake (for SockJS)
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