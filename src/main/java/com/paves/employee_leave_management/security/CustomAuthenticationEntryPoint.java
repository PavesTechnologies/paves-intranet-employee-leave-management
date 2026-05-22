package com.paves.employee_leave_management.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException {

            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            String detail = "Unauthorized";
            String message = "Invalid token provided.";

            // Check if the cause is specifically an expired JWT
            if (authException.getMessage() != null && authException.getMessage().contains("Jwt expired")) {
                detail = "token has expired";
            }

            String jsonResponse = String.format(
                    "{\"detail\": \"%s\"}",
                    detail
            );

            response.getWriter().write(jsonResponse);
        }
}
