package group.moniepoint.eventsnestserver;

import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
@DisplayName("Application context load")
class EventsNestServerApplicationTests {

    @Test
    @DisplayName("Spring context loads successfully with all beans wired")
    void contextLoads() {
    }
}
