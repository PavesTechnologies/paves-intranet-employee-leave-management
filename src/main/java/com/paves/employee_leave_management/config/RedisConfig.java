package com.paves.employee_leave_management.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.username:}")
    private String redisUsername;

    @Value("${spring.data.redis.database:2}")
    private int redisDatabase;

    // Validate config values are present — but do NOT probe the connection here.
    // Connectivity is handled lazily so the app starts even if Redis is down.
    // RedisHealthTracker will detect when Redis comes back and switch over.
    @PostConstruct
    public void validateConfiguration() {
        if (redisHost == null || redisHost.isBlank()) {
            throw new IllegalStateException(
                    "spring.data.redis.host must be configured. " +
                            "Set the REDIS_HOST environment variable.");
        }
        log.info("Redis config — host: {}, port: {}, db: {}", redisHost, redisPort, redisDatabase);
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig =
                new RedisStandaloneConfiguration(redisHost, redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            serverConfig.setPassword(redisPassword);
        }
        if (redisUsername != null && !redisUsername.isEmpty()) {
            serverConfig.setUsername(redisUsername);
        }
        serverConfig.setDatabase(redisDatabase);

        // 5s connect timeout — generous enough for cloud Redis latency (50-200ms)
        // without hanging startup too long if Redis is unreachable.
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .protocolVersion(io.lettuce.core.protocol.ProtocolVersion.RESP2)
                // Immediately reject commands while disconnected rather than
                // queuing them indefinitely — lets SafeRedisCache fail-fast
                // and switch to fallback without blocking the request thread.
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5))
                .clientOptions(clientOptions)
                .build();

        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(serverConfig, clientConfig);

        // INTENTIONALLY false — the app must start even when Redis is down.
        // RedisHealthTracker probes every 30s and SmartCacheManager switches
        // to Redis automatically once it's back up.
        factory.setValidateConnection(false);
        factory.setEagerInitialization(false);

        return factory;
    }

    // Safe serializer — no DefaultTyping.NON_FINAL (CVE-2017-7525 RCE risk).
    // BasicPolymorphicTypeValidator allowlists only our own DTOs plus standard
    // java.util / java.time types. Any class outside this list is rejected
    // before instantiation, blocking gadget-chain deserialization attacks.
    //
    // MIGRATION NOTE: objects serialized with the old NON_FINAL config contain
    // arbitrary class names that the new validator will reject. Flush affected
    // cache keys (or run CacheEvictionOnStartup) before first deployment.
    private GenericJackson2JsonRedisSerializer buildSerializer() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator
                .builder()
                .allowIfBaseType("com.paves.employee_leave_management")
                .allowIfSubType("java.util")
                .allowIfSubType("java.lang")
                .allowIfSubType("java.time")
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                typeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(buildSerializer()))
                .disableCachingNullValues()
                .prefixCacheNameWith("lms:");
    }

    @Bean("redisCacheManager")
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put("employeeLeaveBalance",
                cacheConfiguration().entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("leaveRequestsByEmployee",
                cacheConfiguration().entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("all-leave-types",
                cacheConfiguration().entryTtl(Duration.ofHours(6)));
        cacheConfigs.put("leaveRequestsByEmployeeAndYear",
                cacheConfiguration().entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("pendingLeaveRequestsByEmployeeAndYear",
                cacheConfiguration().entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("holidaysByYear",
                cacheConfiguration().entryTtl(Duration.ofHours(10)));
        cacheConfigs.put("employeesLeaveBalances",
                cacheConfiguration().entryTtl(Duration.ofMinutes(30)));

        // lockingRedisCacheWriter prevents concurrent threads racing on the
        // same cache key (e.g. approve + cancel hitting the same entry).
        // Batch size 10 keeps SCAN from blocking Redis on large keyspaces.
        RedisCacheWriter cacheWriter = RedisCacheWriter.lockingRedisCacheWriter(
                redisConnectionFactory,
                BatchStrategies.scan(10)
        );

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(cacheConfiguration())
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    @Bean("fallbackCacheManager")
    public CacheManager fallbackCacheManager() {
        return new ConcurrentMapCacheManager(
                "employeeLeaveBalance",
                "leaveRequestsByEmployee",
                "all-leave-types",
                "leaveRequestsByEmployeeAndYear",
                "pendingLeaveRequestsByEmployeeAndYear",
                "holidaysByYear",
                "employeesLeaveBalances",
                "employeeLeaveBalanceForDropdown"
        );
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(buildSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(buildSerializer());
        template.afterPropertiesSet();
        return template;
    }
}