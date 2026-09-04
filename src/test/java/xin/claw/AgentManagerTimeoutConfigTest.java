package xin.claw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class AgentManagerTimeoutConfigTest {
    @Test
    void defaultsApiRequestsToThreeMinutes() {
        assertEquals(180, PluginConfig.apiTimeoutSeconds);
        assertEquals(Duration.ofSeconds(180), AgentManager.configuredApiTimeout());
    }
}
