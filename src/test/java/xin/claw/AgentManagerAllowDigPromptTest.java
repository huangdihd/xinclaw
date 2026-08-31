package xin.claw;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;

final class AgentManagerAllowDigPromptTest {
    private static String systemPrompt() throws Exception {
        SystemMessage system = AgentManager.BotAgent.class
            .getMethod("chat", String.class)
            .getAnnotation(SystemMessage.class);
        return String.join("\n", system.value());
    }

    @Test
    void systemPromptExplainsNavigationDoesNotDigByDefault() throws Exception {
        String prompt = systemPrompt();
        assertTrue(prompt.contains("默认不挖掘"), prompt);
        assertTrue(prompt.contains("allowDig"), prompt);
        assertTrue(prompt.contains("不会破坏任何方块"), prompt);
    }

    @Test
    void systemPromptKeepsClosedDoorWorkflowWithNoDigNavigation() throws Exception {
        String prompt = systemPrompt();
        assertTrue(prompt.contains("自动寻路不会自动开门"), prompt);
        assertTrue(prompt.contains("allowDig=true 才允许挖掘"), prompt);
        assertTrue(prompt.contains("禁止通过挖掘建筑墙体"), prompt);
    }
}
