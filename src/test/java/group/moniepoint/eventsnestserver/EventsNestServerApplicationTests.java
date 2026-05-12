package group.moniepoint.eventsnestserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application context load")
class EventsNestServerApplicationTests {

    // Redis excluded from autoconfigure in test profile; mock the beans that
    // depend on a live connection so CacheConfig / RateLimitConfig can wire up.
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private io.lettuce.core.api.StatefulRedisConnection<String, byte[]> rateLimitRedisConnection;
    @MockitoBean private io.github.bucket4j.distributed.proxy.ProxyManager<String> proxyManager;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("Spring context loads successfully with all beans wired")
    void contextLoads() {
    }
}
