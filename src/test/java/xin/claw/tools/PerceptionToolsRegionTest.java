package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import xin.bbtt.Block.BlockState;
import xin.bbtt.world.World;

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
    void radiusSearchSupportsSixtyFourBlocksAndRejectsLargerRequests() {
        Map<String, BlockState> blocks = new HashMap<>();
        blocks.put("60,64,0", block("bookshelf", "block"));
        PerceptionTools tools = tools(blocks, new Vector3d(0.5, 64, 0.5));

        String result = tools.findSpecificBlocks("bookshelf", 60);

        assertTrue(result.contains("(60,64,0)"), result);
        assertTrue(tools.findSpecificBlocks("bookshelf", 65).contains("最大64"));
        assertTrue(tools.findSpecificBlocks("bookshelf", 0.5).contains("1到64"));
        assertTrue(tools.findSpecificBlocks("bookshelf", Double.NaN).contains("1到64"));
        assertTrue(tools.findSpecificBlocks("bookshelf", Double.POSITIVE_INFINITY).contains("1到64"));

        PerceptionTools invalidPosition = tools(Map.of(), new Vector3d(Double.NaN, 64, 0));
        assertTrue(invalidPosition.findSpecificBlocks("bookshelf", 10).contains("当前坐标无效"));
        PerceptionTools outsideWorld = tools(Map.of(), new Vector3d(30_000_001, 64, 0));
        assertTrue(outsideWorld.findSpecificBlocks("bookshelf", 10).contains("当前坐标无效"));
    }

    @Test
    void fuzzySearchDoesNotMistakeStairsForAir() {
        Map<String, BlockState> blocks = new HashMap<>();
        blocks.put("1,64,0", block("oak_stairs", "block"));
        PerceptionTools tools = tools(blocks, new Vector3d(0.5, 64, 0.5));

        String result = tools.findSpecificBlocks("stairs", 2);

        assertTrue(result.contains("oak_stairs"), result);
    }

    @Test
    void broadSearchCountsAllMatchesButFormatsOnlyNearestThirty() {
        AtomicInteger formattingCalls = new AtomicInteger();
        Map<String, String> countingProperties = new AbstractMap<>() {
            @Override public boolean isEmpty() { return false; }
            @Override public Set<Entry<String, String>> entrySet() {
                formattingCalls.incrementAndGet();
                return Set.of(Map.entry("variant", "test"));
            }
        };
        BlockState sandstone = new BlockState(
            "sandstone", 0, countingProperties, "block", 1.0, true, "test");
        Map<String, BlockState> blocks = new HashMap<>();
        int inserted = 0;
        for (int x = -2; x <= 2 && inserted < 40; x++) {
            for (int y = 63; y <= 65 && inserted < 40; y++) {
                for (int z = -2; z <= 2 && inserted < 40; z++) {
                    blocks.put(x + "," + y + "," + z, sandstone);
                    inserted++;
                }
            }
        }
        PerceptionTools tools = tools(blocks, new Vector3d(0.5, 64, 0.5));

        String result = tools.findSpecificBlocks("sandstone", 4);

        assertTrue(result.contains("共找到 40 个"), result);
        assertTrue(result.contains("前 30 个"), result);
        assertTrue(formattingCalls.get() == 30,
            "only retained results should be formatted, actual=" + formattingCalls.get());
    }

    @Test
    void listsEveryLoadedChunkInStableOrderWithBlockBounds() {
        PerceptionTools tools = new PerceptionTools(
            (x, y, z) -> block("air", "empty"),
            () -> new Vector3d(0.5, 64, 0.5),
            () -> Set.of(
                new World.ChunkPosition(4, -5),
                new World.ChunkPosition(-2, 3),
                new World.ChunkPosition(0, 0)
            )
        );

        String result = tools.getLoadedChunks();

        assertTrue(result.contains("共3个"), result);
        assertTrue(result.contains("总方块XZ范围: x=[-32,79], z=[-80,63]"), result);
        assertTrue(result.contains("chunk(-2,3) blocks x=[-32,-17] z=[48,63]"), result);
        assertTrue(result.contains("chunk(4,-5) blocks x=[64,79] z=[-80,-65]"), result);
        assertTrue(result.indexOf("chunk(-2,3)") < result.indexOf("chunk(0,0)"));
        assertTrue(result.indexOf("chunk(0,0)") < result.indexOf("chunk(4,-5)"));
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
    void boundedSearchDoesNotMistakeStairsForAir() {
        PerceptionTools tools = tools(
            Map.of("10,64,20", block("oak_stairs", "block")),
            new Vector3d(9.5, 64, 20.5)
        );

        String result = tools.findSpecificBlocksInBounds(
            "stairs", new int[] {10, 64, 20}, new int[] {11, 65, 21}, 20
        );

        assertTrue(result.contains("oak_stairs"), result);
        assertTrue(result.contains("(10,64,20)"), result);
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
