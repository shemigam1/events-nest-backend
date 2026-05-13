package group.moniepoint.eventsnestserver.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared test infrastructure configuration.
 *
 * Provides mock beans for all external-service dependencies so
 * that @SpringBootTest integration tests can boot without a live
 * Redis server. Import this class with @Import(IntegrationTestConfig.class)
 * on any @SpringBootTest that needs it.
 *
 * Why @TestConfiguration + explicit @Bean instead of @MockitoBean?
 *   @MockitoBean on SimpMessagingTemplate conflicts with the bean that
 *   @EnableWebSocketMessageBroker already registers. @TestConfiguration
 *   beans are added to the context BEFORE the application beans are
 *   processed, so @ConditionalOnMissingBean in RateLimitConfig /
 *   CacheConfig sees them and skips the methods that open real sockets.
 */
@TestConfiguration
public class IntegrationTestConfig {

    /**
     * Replaces the Redis-backed CacheManager with an in-memory one so cache
     * annotations work without a live Redis connection during integration tests.
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    /**
     * Satisfies RateLimitConfig.rateLimitRedisConnection().
     * With @ConditionalOnMissingBean on the production @Bean method,
     * this mock causes that method body (which calls RedisClient.connect())
     * to be skipped entirely.
     */
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection() {
        return mock(StatefulRedisConnection.class);
    }

    /**
     * Satisfies RateLimitFilter's ProxyManager<String> injection point.
     */
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public ProxyManager<String> proxyManager() {
        return mock(ProxyManager.class);
    }

    /**
     * Replaces the real KafkaTemplate with a no-op mock so audit publish calls
     * during integration tests don't attempt to reach a real broker.
     */
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> mock = mock(KafkaTemplate.class);
        when(mock.send(any(String.class), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        return mock;
    }
}
