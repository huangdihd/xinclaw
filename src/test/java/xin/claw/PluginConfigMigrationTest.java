package xin.claw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

final class PluginConfigMigrationTest {
    @Test
    void existingConfigReceivesNewKeysAndDropsDeprecatedThinkingFlag() {
        Properties properties = new Properties();
        properties.setProperty("api_key", "keep-me");
        properties.setProperty("enable_thinking", "true");

        boolean changed = PluginConfig.migrateProperties(properties);

        assertTrue(changed);
        assertEquals("keep-me", properties.getProperty("api_key"));
        assertEquals("high", properties.getProperty("reasoning_effort"));
        assertEquals("", properties.getProperty("soul"));
        assertEquals(
            PluginConfig.DEFAULT_PUBLIC_CHAT_FORBIDDEN_COORDINATE_RANGE,
            properties.getProperty("public_chat_forbidden_coordinate_range")
        );
        assertFalse(properties.containsKey("enable_thinking"));
        assertFalse(PluginConfig.migrateProperties(properties),
            "a second migration must not rewrite an already current config");
    }
}
