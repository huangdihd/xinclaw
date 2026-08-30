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
        assertTrue(prompt.contains("previewPathTo 是绝对坐标点的只读路径预览"));
        assertTrue(prompt.contains("只读路径预览"));
        assertTrue(prompt.contains("pathfindToBounds"));
        assertTrue(prompt.contains("max_exclusive"));
        assertTrue(prompt.contains("bounds"));
        assertTrue(prompt.contains("CLMCP Rank 1"));
        assertTrue(prompt.contains("如果工具列表中提供 getLoadedVoxelSearchPlan 和 searchVoxelRegion"));
        assertTrue(prompt.contains("开放词汇寻找结构或对象"));
        assertTrue(prompt.contains("必须保留用户目标中的完整判别性名词短语"));
        assertTrue(prompt.contains("pillager outpost watchtower"));
        assertTrue(prompt.contains("不得缩短成 watchtower"));
        assertTrue(prompt.contains("必须优先调用 getLoadedVoxelSearchPlan"));
        assertTrue(prompt.contains("不得先平铺 getAreaMapAt"));
        assertTrue(prompt.contains("语义候选区域，不是精确站立点或入口"));
        assertTrue(prompt.contains("Rank 1 的 min/max_exclusive 数组原样传给 getAreaMapAt"));
        assertTrue(prompt.contains("mapMinY=地面Y-1"));
        assertTrue(prompt.contains("mapMaxYExclusive=地面Y+4"));
        assertTrue(prompt.contains("一次覆盖候选XZ和入口层"));
        assertTrue(prompt.contains("精确可达点"));
        assertFalse(prompt.contains("立即调用 pathfindToBounds"));
        assertFalse(prompt.contains("不得在远处用 getAreaMapAt"));

        Method areaMapAt = PerceptionTools.class.getMethod(
            "getAreaMapAt",
            int[].class, int[].class, int.class, int.class
        );
        String areaMapDescription = String.join("\n", areaMapAt.getAnnotation(Tool.class).value());
        assertTrue(areaMapDescription.contains("直接使用 searchVoxelRegion 返回"));
        assertTrue(areaMapDescription.contains("Rank-1 bounds"));
        assertTrue(areaMapDescription.contains("候选内部的几何细化"));
        assertTrue(areaMapDescription.contains("不得脱离候选范围扫描任意远程坐标"));
        Method pointMap = PerceptionTools.class.getMethod(
            "getAreaMapAtPoint",
            int.class, int.class, int.class, int.class, int.class, int.class
        );
        String pointDescription = String.join("\n", pointMap.getAnnotation(Tool.class).value());
        assertTrue(pointDescription.contains("普通地图分析"));
        assertFalse(areaMapDescription.contains("到达前不得"));
    }
}
