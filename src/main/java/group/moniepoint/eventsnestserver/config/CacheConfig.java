package group.moniepoint.eventsnestserver.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.annotation.EnableCaching;
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
public class CacheConfig {

    /**
     * Default: 5 minutes TTL, JSON values.
     * Per-cache overrides are declared in the customTtls map below — add
     * new caches here without touching the callers.
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

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
