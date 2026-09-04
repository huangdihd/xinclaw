package xin.claw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.agent.tool.Tool;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import xin.claw.tools.PerceptionTools;

final class AgentManagerRegionToolPromptTest {
    @Test
    void systemPromptDescribesRegionToolsWithoutExternalModelCoupling() throws Exception {
        Method chat = AgentManager.BotAgent.class.getMethod("chat", String.class);
        SystemMessage annotation = chat.getAnnotation(SystemMessage.class);
        String prompt = String.join("\n", annotation.value());

        assertTrue(prompt.contains("findSpecificBlocksInBounds"));
        assertTrue(prompt.contains("findReachablePointInBounds"));
        assertTrue(prompt.contains("previewPathToBounds"));
        assertTrue(prompt.contains("pathfindToBounds"));
        assertTrue(prompt.contains("min 包含"));
        assertTrue(prompt.contains("max_exclusive 不包含"));

        Method areaMapAt = PerceptionTools.class.getMethod(
            "getAreaMapAt",
            int[].class, int[].class, int.class, int.class
        );
        String areaMapDescription = String.join("\n", areaMapAt.getAnnotation(Tool.class).value());
        assertTrue(areaMapDescription.contains("指定半开 bounds"));
        assertTrue(areaMapDescription.contains("最多5个绝对Y层"));
        assertFalse(areaMapDescription.contains("Rank-1"));
        assertFalse(areaMapDescription.contains("必须原样复制"));
        assertFalse(areaMapDescription.contains("不得脱离候选范围"));

        Method pointMap = PerceptionTools.class.getMethod(
            "getAreaMapAtPoint",
            int.class, int.class, int.class, int.class, int.class, int.class
        );
        String pointDescription = String.join("\n", pointMap.getAnnotation(Tool.class).value());
        assertTrue(pointDescription.contains("任意绝对坐标"));
        assertFalse(pointDescription.contains("应优先使用"));
    }
}
