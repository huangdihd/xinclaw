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
    void systemPromptExplainsHowToChainSemanticBoundsIntoRegionTools() throws Exception {
        Method chat = AgentManager.BotAgent.class.getMethod("chat", String.class);
        SystemMessage annotation = chat.getAnnotation(SystemMessage.class);
        String prompt = String.join("\n", annotation.value());

        assertTrue(prompt.contains("searchVoxelRegion"));
        assertTrue(prompt.contains("findSpecificBlocksInBounds"));
        assertTrue(prompt.contains("getAreaMapAt"));
        assertTrue(prompt.contains("findReachablePointInBounds"));
        assertTrue(prompt.contains("previewPathToBounds"));
        assertTrue(prompt.contains("只读路径预览"));
        assertTrue(prompt.contains("pathfindToBounds"));
        assertTrue(prompt.contains("max_exclusive"));
        assertTrue(prompt.contains("bounds"));
        assertTrue(prompt.contains("行动验证"));
        assertTrue(prompt.contains("立即调用 pathfindToBounds"));
        assertTrue(prompt.contains("不得在远处用 getAreaMapAt"));
        assertTrue(prompt.contains("到达候选后"));

        Method areaMapAt = PerceptionTools.class.getMethod(
            "getAreaMapAt",
            int.class, int.class, int.class, int.class, int.class, int.class
        );
        String areaMapDescription = String.join("\n", areaMapAt.getAnnotation(Tool.class).value());
        assertTrue(areaMapDescription.contains("普通地图分析任务"));
        assertTrue(areaMapDescription.contains("CLMCP 导航任务"));
        assertTrue(areaMapDescription.contains("到达前不得"));
        assertFalse(areaMapDescription.contains("而不要求机器人先移动到那里"));
    }
}
