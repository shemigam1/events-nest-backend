package group.moniepoint.eventsnestserver.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires a Bucket4j {@link ProxyManager} backed by Redis via Lettuce.
 *
 * <p>We create a dedicated Lettuce client here rather than reusing the one
 * Spring Data Redis manages internally, because Bucket4j needs access to the
 * raw {@link StatefulRedisConnection} with a {@code String/byte[]} codec — a
 * lower-level API than {@code RedisTemplate}.
 *
 * <p>Profile-gated to {@code !test} — the {@code @SpringBootTest} context-load
 * check doesn't run Redis, so this config (and the {@code RateLimitFilter}
 * that depends on it) would otherwise fail at bean instantiation.
 */
@Configuration
@Profile("!test")
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection() {
        RedisClient client = RedisClient.create(
                RedisURI.builder().withHost(redisHost).withPort(redisPort).build());
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    public ProxyManager<String> proxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .build();
    }
}
