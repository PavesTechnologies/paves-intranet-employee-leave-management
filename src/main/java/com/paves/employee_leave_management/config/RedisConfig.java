package com.paves.employee_leave_management.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import org.springframework.context.annotation.Primary;
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

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig =
                new RedisStandaloneConfiguration(redisHost, redisPort);

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .clientOptions(clientOptions)
                .build();

        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.setValidateConnection(false);
        factory.setEagerInitialization(false);
        return factory;
    }

    private GenericJackson2JsonRedisSerializer buildSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
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


        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
                redisConnectionFactory,
                BatchStrategies.scan(100)
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
                "holidaysByYear"
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