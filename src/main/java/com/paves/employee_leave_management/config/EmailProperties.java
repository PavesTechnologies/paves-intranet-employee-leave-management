package com.paves.employee_leave_management.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Properties;

/**
 * Configuration properties for email service.
 * Maps application.properties entries with prefix "email" to Java configuration object.
 * 
 * @author Email Service
 * @version 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "email")
@Validated
@Slf4j
public class EmailProperties {
    
    /**
     * SMTP Configuration
     */
    @NotBlank(message = "Email host cannot be blank")
    private String host = "smtp.office365.com";
    
    @Min(value = 1, message = "Port must be greater than 0")
    @Max(value = 65535, message = "Port must be less than 65536")
    private int port = 587;
    
    @NotBlank(message = "Email username cannot be blank")
    private String username;
    
    @NotBlank(message = "Email password cannot be blank")
    private String password;
    
    /**
     * Security Configuration
     */
    private boolean tlsEnabled = true;
    private boolean sslEnabled = false;
    private boolean authEnabled = true;
    private boolean startTlsEnabled = true;
    
    /**
     * Connection Configuration
     */
    @Min(value = 1000, message = "Connection timeout must be at least 1000ms")
    private int connectionTimeout = 30000; // 30 seconds
    
    @Min(value = 1000, message = "Read timeout must be at least 1000ms")
    private int readTimeout = 30000; // 30 seconds
    
    @Min(value = 1000, message = "Write timeout must be at least 1000ms")
    private int writeTimeout = 30000; // 30 seconds
    
    /**
     * Connection Pool Configuration
     */
    @Min(value = 1, message = "Pool size must be at least 1")
    @Max(value = 50, message = "Pool size cannot exceed 50")
    private int poolSize = 10;
    
    @Min(value = 1, message = "Max pool size must be at least 1")
    @Max(value = 100, message = "Max pool size cannot exceed 100")
    private int maxPoolSize = 20;
    
    @Min(value = 1000, message = "Pool timeout must be at least 1000ms")
    private long poolTimeout = 60000; // 60 seconds
    
    /**
     * Async Configuration
     */
    @Min(value = 1, message = "Core pool size must be at least 1")
    private int asyncCorePoolSize = 5;
    
    @Min(value = 1, message = "Max pool size must be at least 1")
    private int asyncMaxPoolSize = 20;
    
    @Min(value = 1, message = "Queue capacity must be at least 1")
    private int asyncQueueCapacity = 100;
    
    private String asyncThreadNamePrefix = "email-async-";
    
    /**
     * Email Configuration
     */
    @NotBlank(message = "Default from address cannot be blank")
    private String defaultFromAddress;
    
    private String defaultFromName = "PAVES Employee Leave Management";
    
    /**
     * Retry Configuration
     */
    @Min(value = 0, message = "Max retry attempts cannot be negative")
    @Max(value = 5, message = "Max retry attempts cannot exceed 5")
    private int maxRetryAttempts = 3;
    
    @Min(value = 1000, message = "Retry delay must be at least 1000ms")
    private long retryDelayMs = 5000; // 5 seconds
    
    /**
     * Debug and Logging
     */
    private boolean debugEnabled = false;
    private boolean logEmailContent = false;
    
    /**
     * Validation Configuration
     */
    private boolean strictEmailValidation = true;
    private String emailValidationRegex = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$";
    
    /**
     * Rate Limiting
     */
    @Min(value = 1, message = "Max emails per minute must be at least 1")
    private int maxEmailsPerMinute = 60;
    
    @Min(value = 1, message = "Max emails per hour must be at least 1")
    private int maxEmailsPerHour = 1000;
    
    /**
     * Initialize method to log configuration after properties are loaded
     */
    public void logConfiguration() {
        log.info("Email service configuration loaded:");
        log.info("  Host: {}", host);
        log.info("  Port: {}", port);
        log.info("  Username: {}", username != null ? username.replaceAll("(.{3}).*(@.*)", "$1***$2") : "NOT SET");
        log.info("  TLS Enabled: {}", tlsEnabled);
        log.info("  Auth Enabled: {}", authEnabled);
        log.info("  Connection Timeout: {}ms", connectionTimeout);
        log.info("  Pool Size: {} (Max: {})", poolSize, maxPoolSize);
        log.info("  Async Pool Size: {} (Max: {})", asyncCorePoolSize, asyncMaxPoolSize);
        log.info("  Default From: {} <{}>", defaultFromName, defaultFromAddress);
        log.info("  Max Retry Attempts: {}", maxRetryAttempts);
        log.info("  Debug Enabled: {}", debugEnabled);
    }
    
    /**
     * Validates that required properties are set
     * 
     * @throws IllegalStateException if required properties are missing
     */
    public void validateConfiguration() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("Email username must be configured");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalStateException("Email password must be configured");
        }
        if (defaultFromAddress == null || defaultFromAddress.trim().isEmpty()) {
            throw new IllegalStateException("Default from address must be configured");
        }
        
        log.info("Email configuration validation passed");
    }
    
    /**
     * Creates Properties object for JavaMail Session configuration
     * 
     * @return Properties configured for SMTP connection
     */
    public Properties createTestProperties() {
        Properties props = new Properties();
        
        // SMTP Configuration
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(authEnabled));
        
        // Security Configuration
        props.put("mail.smtp.starttls.enable", String.valueOf(startTlsEnabled));
        props.put("mail.smtp.ssl.enable", String.valueOf(sslEnabled));
        
        // Connection Configuration
        props.put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout));
        props.put("mail.smtp.timeout", String.valueOf(readTimeout));
        props.put("mail.smtp.writetimeout", String.valueOf(writeTimeout));
        
        // Additional SMTP properties for Outlook/Office365
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.ssl.trust", host);
        
        if (debugEnabled) {
            props.put("mail.debug", "true");
        }
        
        log.debug("Created SMTP properties for host: {} port: {}", host, port);
        return props;
    }
}
