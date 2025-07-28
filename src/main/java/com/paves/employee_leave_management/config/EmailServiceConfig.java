package com.paves.employee_leave_management.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Spring Configuration for Email Service.
 * Creates JavaMailSender bean, configures connection pooling, and sets up async executor.
 * 
 * @author Email Service
 * @version 1.0
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class EmailServiceConfig {
    
    private final EmailProperties emailProperties;
    private ThreadPoolTaskExecutor emailTaskExecutor;
    
    /**
     * Creates and configures JavaMailSender bean with connection pooling
     * 
     * @return Configured JavaMailSender instance
     */
    @Bean(name = "javaMailSender")
    public JavaMailSender javaMailSender() {
        log.info("Initializing JavaMailSender with Outlook SMTP configuration");
        
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        
        // Basic SMTP configuration
        mailSender.setHost(emailProperties.getHost());
        mailSender.setPort(emailProperties.getPort());
        mailSender.setUsername(emailProperties.getUsername());
        mailSender.setPassword(emailProperties.getPassword());
        
        // Default encoding
        mailSender.setDefaultEncoding("UTF-8");
        
        // JavaMail properties for Outlook/Office365
        Properties props = mailSender.getJavaMailProperties();
        
        // Transport protocol
        props.put("mail.transport.protocol", "smtp");
        
        // Authentication
        props.put("mail.smtp.auth", emailProperties.isAuthEnabled());
        
        // TLS/SSL Configuration
        props.put("mail.smtp.starttls.enable", emailProperties.isStartTlsEnabled());
        props.put("mail.smtp.starttls.required", emailProperties.isTlsEnabled());
        props.put("mail.smtp.ssl.enable", emailProperties.isSslEnabled());
        
        // Connection timeouts
        props.put("mail.smtp.connectiontimeout", emailProperties.getConnectionTimeout());
        props.put("mail.smtp.timeout", emailProperties.getReadTimeout());
        props.put("mail.smtp.writetimeout", emailProperties.getWriteTimeout());
        
        // Connection pooling
        props.put("mail.smtp.connectionpoolsize", emailProperties.getPoolSize());
        props.put("mail.smtp.connectionpooltimeout", emailProperties.getPoolTimeout());
        
        // Additional Outlook-specific properties
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.ssl.ciphersuites", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
        
        // Debug settings
        if (emailProperties.isDebugEnabled()) {
            props.put("mail.debug", "true");
            props.put("mail.debug.auth", "true");
        }
        
        // Additional reliability settings
        props.put("mail.smtp.quitwait", "false");
        props.put("mail.smtp.socketFactory.fallback", "false");
        
        log.info("JavaMailSender configured successfully for host: {} on port: {}", 
                emailProperties.getHost(), emailProperties.getPort());
        
        return mailSender;
    }
    
    /**
     * Creates async task executor for email operations
     * 
     * @return Configured ThreadPoolTaskExecutor for async email operations
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        log.info("Initializing email async task executor");
        
        emailTaskExecutor = new ThreadPoolTaskExecutor();
        
        // Core pool configuration
        emailTaskExecutor.setCorePoolSize(emailProperties.getAsyncCorePoolSize());
        emailTaskExecutor.setMaxPoolSize(emailProperties.getAsyncMaxPoolSize());
        emailTaskExecutor.setQueueCapacity(emailProperties.getAsyncQueueCapacity());
        
        // Thread naming
        emailTaskExecutor.setThreadNamePrefix(emailProperties.getAsyncThreadNamePrefix());
        
        // Thread lifecycle
        emailTaskExecutor.setKeepAliveSeconds(60);
        emailTaskExecutor.setAllowCoreThreadTimeOut(true);
        
        // Rejection policy - caller runs policy to prevent email loss
        emailTaskExecutor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        // Wait for tasks to complete on shutdown
        emailTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        emailTaskExecutor.setAwaitTerminationSeconds(30);
        
        // Initialize the executor
        emailTaskExecutor.initialize();
        
        log.info("Email task executor initialized with core pool size: {}, max pool size: {}, queue capacity: {}", 
                emailProperties.getAsyncCorePoolSize(), 
                emailProperties.getAsyncMaxPoolSize(), 
                emailProperties.getAsyncQueueCapacity());
        
        return emailTaskExecutor;
    }
    
    /**
     * Post-construct initialization
     */
    @PostConstruct
    public void init() {
        log.info("Email Service Configuration initialized");
        
        // Validate configuration
        emailProperties.validateConfiguration();
        
        // Log configuration details
        emailProperties.logConfiguration();
        
        log.info("Email service is ready for async operations");
    }
    
    /**
     * Pre-destroy cleanup
     */
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down email service configuration");
        
        if (emailTaskExecutor != null) {
            log.info("Shutting down email task executor");
            emailTaskExecutor.shutdown();
            
            try {
                if (!emailTaskExecutor.getThreadPoolExecutor().awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("Email task executor did not terminate gracefully, forcing shutdown");
                    emailTaskExecutor.getThreadPoolExecutor().shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for email task executor shutdown", e);
                emailTaskExecutor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("Email service configuration cleanup completed");
    }
    
    /**
     * Creates properties for testing email connection
     * 
     * @return Properties configured for connection testing
     */
    public Properties createTestProperties() {
        Properties testProps = new Properties();
        
        testProps.put("mail.smtp.host", emailProperties.getHost());
        testProps.put("mail.smtp.port", emailProperties.getPort());
        testProps.put("mail.smtp.auth", emailProperties.isAuthEnabled());
        testProps.put("mail.smtp.starttls.enable", emailProperties.isStartTlsEnabled());
        testProps.put("mail.smtp.connectiontimeout", Math.min(emailProperties.getConnectionTimeout(), 10000)); // Max 10s for tests
        testProps.put("mail.smtp.timeout", Math.min(emailProperties.getReadTimeout(), 10000));
        
        return testProps;
    }
}
