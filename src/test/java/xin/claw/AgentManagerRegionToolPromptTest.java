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
    void systemPromptDescribesSemanticToolsWithoutPrescribingAWorkflow() throws Exception {
        Method chat = AgentManager.BotAgent.class.getMethod("chat", String.class);
        SystemMessage annotation = chat.getAnnotation(SystemMessage.class);
        String prompt = String.join("\n", annotation.value());

        assertTrue(prompt.contains("searchLoadedVoxelRegions"));
        assertTrue(prompt.contains("searchVoxelRegion"));
        assertTrue(prompt.contains("语义候选区域"));
        assertTrue(prompt.contains("相似度不是概率"));
        assertTrue(prompt.contains("由你根据任务和已有证据决定"));
        assertTrue(prompt.contains("没有固定工具链或强制 rank 顺序"));
        assertTrue(prompt.contains("bounds.min"));
        assertTrue(prompt.contains("max_exclusive"));
        assertFalse(prompt.contains("必须优先调用 searchLoadedVoxelRegions"));
        assertFalse(prompt.contains("不得先平铺 getAreaMapAt"));
        assertFalse(prompt.contains("Rank 1 的 min/max_exclusive 数组原样传给 getAreaMapAt"));
        assertFalse(prompt.contains("mapMinY=地面Y-1"));
        assertFalse(prompt.contains("mapMaxYExclusive=地面Y+4"));
        assertFalse(prompt.contains("只有候选内证据证伪 Rank 1 后才尝试下一 rank"));

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
