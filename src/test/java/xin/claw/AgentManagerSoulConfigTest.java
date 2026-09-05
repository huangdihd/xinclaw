package xin.claw;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;

final class AgentManagerSoulConfigTest {
    @Test
    void configuredSoulIsAppendedToTheSystemPrompt() {
        String originalSoul = PluginConfig.soul;
        try {
            PluginConfig.soul = "你珍惜建筑，不主动破坏玩家作品。";

            String prompt = AgentManager.configuredSystemPrompt();

            assertTrue(prompt.contains("【自定义 Soul】"), prompt);
            assertTrue(prompt.contains(PluginConfig.soul), prompt);
            assertTrue(prompt.contains("必须在当前回复中调用至少一个实际工具"),
                "custom soul must extend rather than replace the built-in safety prompt");
            assertTrue(prompt.contains("配置保护范围内的 XYZ 或 X/Z 坐标"), prompt);
        } finally {
            PluginConfig.soul = originalSoul;
        }
    }

    @Test
    void dynamicProviderIsNotShadowedByAStaticSystemMessageAnnotation() throws Exception {
        assertNull(AgentManager.BotAgent.class.getMethod("chat", String.class)
            .getAnnotation(SystemMessage.class));
    }
}
