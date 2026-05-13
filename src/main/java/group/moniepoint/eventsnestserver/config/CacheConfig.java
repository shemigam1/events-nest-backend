package group.moniepoint.eventsnestserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new ResilientCacheErrorHandler();
    }

    @Slf4j
    static class ResilientCacheErrorHandler implements CacheErrorHandler {

        @Override
        public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Cache GET failed on '{}' key='{}' — falling through to data source. Cause: {}",
                    cache.getName(), key, ex.getMessage());
            // Evict the corrupt entry so the next PUT re-caches clean data
            try { cache.evict(key); } catch (Exception ignored) {}
        }

        @Override
        public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
            log.warn("Cache PUT failed on '{}' key='{}': {}", cache.getName(), key, ex.getMessage());
        }

        @Override
        public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Cache EVICT failed on '{}' key='{}': {}", cache.getName(), key, ex.getMessage());
        }

        @Override
        public void handleCacheClearError(RuntimeException ex, Cache cache) {
            log.warn("Cache CLEAR failed on '{}': {}", cache.getName(), ex.getMessage());
        }
    }

    /**
     * Default: 5 minutes TTL, JSON values.
     * Per-cache overrides are declared in the customTtls map below — add
     * new caches here without touching the callers.
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Create ObjectMapper with JSR310 module for Java 8 date/time types
        // and enable type information so Redis can deserialize objects correctly
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(),
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));

        Map<String, RedisCacheConfiguration> customTtls = Map.of(
                // QR look-ups are hot reads during check-in; 5 min is fine.
                "tickets-by-qr",       defaults.entryTtl(Duration.ofMinutes(5)),
                // Public event list — 2 min so new approvals show quickly.
                "public-events",       defaults.entryTtl(Duration.ofMinutes(2)),
                // Per-event detail — 3 min; evicted immediately on any write.
                "event-detail",        defaults.entryTtl(Duration.ofMinutes(3)),
                // User by email — called on every authenticated request; 10 min.
                "user-by-email",       defaults.entryTtl(Duration.ofMinutes(10)),
                // Programme items — static once published; 15 min.
                "event-programme",     defaults.entryTtl(Duration.ofMinutes(15)),
                // Event config — almost never changes; 15 min.
                "event-config",        defaults.entryTtl(Duration.ofMinutes(15)),
                // Vendor marketplace — aggregation queries; 5 min.
                "vendor-marketplace",  defaults.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(customTtls)
                .build();
    }
}
