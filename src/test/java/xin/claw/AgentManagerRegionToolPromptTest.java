package xin.claw;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.service.SystemMessage;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

final class AgentManagerRegionToolPromptTest {
    @Test
    void systemPromptExplainsHowToChainSemanticBoundsIntoRegionTools() throws Exception {
        Method chat = AgentManager.BotAgent.class.getMethod("chat", String.class);
        SystemMessage annotation = chat.getAnnotation(SystemMessage.class);
        String prompt = String.join("\n", annotation.value());

        assertTrue(prompt.contains("searchVoxelRegion"));
        assertTrue(prompt.contains("findSpecificBlocksInBounds"));
        assertTrue(prompt.contains("getAreaMapAt"));
        assertTrue(prompt.contains("bounds"));
    }
}
