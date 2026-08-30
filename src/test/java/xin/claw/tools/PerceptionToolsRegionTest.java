package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import xin.bbtt.Block.BlockState;

final class PerceptionToolsRegionTest {
    private static BlockState block(String name, String boundingBox) {
        return new BlockState(name, 0, Map.of(), boundingBox, 1.0, true, "test");
    }

    private static PerceptionTools tools(Map<String, BlockState> blocks, Vector3d player) {
        BlockState air = block("air", "empty");
        return new PerceptionTools(
            (x, y, z) -> blocks.getOrDefault(x + "," + y + "," + z, air),
            () -> new Vector3d(player)
        );
    }

    @Test
    void findsMatchingBlocksOnlyInsideHalfOpenBounds() {
        Map<String, BlockState> blocks = new HashMap<>();
        blocks.put("10,64,20", block("dark_oak_log", "block"));
        blocks.put("11,64,20", block("cobblestone", "block"));
        blocks.put("12,64,20", block("dark_oak_log", "block"));
        PerceptionTools tools = tools(blocks, new Vector3d(9.5, 64, 20.5));

        String result = tools.findSpecificBlocksInBounds(
            "dark_oak", new int[] {10, 63, 19}, new int[] {12, 66, 22}, 20
        );

        assertTrue(result.contains("(10,64,20)"));
        assertFalse(result.contains("(12,64,20)"));
        assertTrue(result.contains("半开区间"));
    }

    @Test
    void rejectsOversizedBoundedBlockSearch() {
        PerceptionTools tools = tools(Map.of(), new Vector3d());

        String result = tools.findSpecificBlocksInBounds(
            "stone", new int[] {0, 0, 0}, new int[] {65, 65, 65}, 20
        );

        assertTrue(result.contains("262144"));
    }

    @Test
    void rejectsBoundsArraysThatAreNotThreeDimensional() {
        PerceptionTools tools = tools(Map.of(), new Vector3d());

        String result = tools.findSpecificBlocksInBounds(
            "stone", new int[] {0, 0}, new int[] {1, 1, 1}, 20
        );

        assertTrue(result.contains("3个整数"));
    }

    @Test
    void rendersMapAroundAbsoluteCenterAndMarksPlayerSeparately() {
        Map<String, BlockState> blocks = new HashMap<>();
        blocks.put("101,70,200", block("cobblestone", "block"));
        PerceptionTools tools = tools(blocks, new Vector3d(98.5, 70, 200.5));

        String result = tools.getAreaMapAtPoint(100, 70, 200, 2, 0, 0);

        assertTrue(result.contains("以指定中心(100, 70, 200)"));
        assertTrue(result.contains("+=指定中心"));
        assertTrue(result.contains("@=你"));
        assertTrue(result.contains("A=cobblestone"));
    }

    @Test
    void rendersCandidateMapFromHalfOpenBoundsAndAbsoluteYLayers() {
        Map<String, BlockState> blocks = new HashMap<>();
        blocks.put("10,71,20", block("oak_planks", "block"));
        blocks.put("13,72,23", block("cobblestone", "block"));
        PerceptionTools tools = tools(blocks, new Vector3d(30.5, 71, 30.5));

        String result = tools.getAreaMapAt(
            new int[] {10, 64, 20}, new int[] {14, 96, 24}, 70, 75
        );

        assertTrue(result.contains("来源 bounds=[[10,64,20],[14,96,24])"));
        assertTrue(result.contains("绝对Y层=[70,75)"));
        assertTrue(result.contains("oak_planks"));
        assertTrue(result.contains("cobblestone"));
    }

    @Test
    void boundedMapRejectsOversizedHorizontalOrVerticalProjection() {
        PerceptionTools tools = tools(Map.of(), new Vector3d());

        assertTrue(tools.getAreaMapAt(
            new int[] {0, 0, 0}, new int[] {34, 10, 10}, 0, 5
        ).contains("水平边长"));
        assertTrue(tools.getAreaMapAt(
            new int[] {0, 0, 0}, new int[] {10, 10, 10}, 0, 6
        ).contains("最多5层"));
    }
}
