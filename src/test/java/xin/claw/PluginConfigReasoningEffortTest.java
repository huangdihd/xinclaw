package xin.claw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class PluginConfigReasoningEffortTest {
    @Test
    void acceptsSupportedReasoningEffortValuesCaseInsensitively() {
        assertEquals("low", PluginConfig.normalizeReasoningEffort(" LOW "));
        assertEquals("medium", PluginConfig.normalizeReasoningEffort("medium"));
        assertEquals("high", PluginConfig.normalizeReasoningEffort("HIGH"));
    }

    @Test
    void noneDisablesReasoningAndInvalidValuesFailClosed() {
        assertEquals("none", PluginConfig.normalizeReasoningEffort("none"));
        assertEquals("none", PluginConfig.normalizeReasoningEffort("maximum"));
        assertEquals("none", PluginConfig.normalizeReasoningEffort(null));
    }
}
