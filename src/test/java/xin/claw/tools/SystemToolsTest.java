package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SystemToolsTest {
    @Test
    void benchmarkModeRejectsAllCommandsBeforeBotAccess() {
        SystemTools tools = new SystemTools(false);

        assertEquals(
            "Command execution is disabled for this benchmark.",
            tools.sendCommand("locate structure minecraft:pillager_outpost")
        );
    }
}
