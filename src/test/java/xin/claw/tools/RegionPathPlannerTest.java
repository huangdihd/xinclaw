package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import xin.bbtt.Block.BlockState;
import xin.bbtt.pathfinding.Node;

final class RegionPathPlannerTest {
    private static BlockState block(String name, String boundingBox) {
        return new BlockState(name, 0, Map.of(), boundingBox, 1.0, true, "test");
    }

    @Test
    void skipsUnreachableNearestCandidateAndReturnsNextReachablePoint() {
        BlockState air = block("air", "empty");
        BlockState stone = block("stone", "block");
        Map<String, BlockState> blocks = new HashMap<>();
        for (int x = 10; x <= 12; x++) blocks.put(x + ",63,20", stone);

        RegionPathPlanner.Result result = RegionPathPlanner.findNearestReachable(
            new Node(8, 64, 20),
            new RegionPathPlanner.Bounds(10, 64, 20, 13, 65, 21),
            (x, y, z) -> blocks.getOrDefault(x + "," + y + "," + z, air),
            (start, goal) -> goal.x == 10 ? 0 : goal.x == 11 ? 5 : 4,
            64
        ).orElseThrow();

        assertEquals(new Node(11, 64, 20), result.target());
        assertEquals(5, result.pathLength());
        assertEquals(3, result.standableCandidates());
        assertEquals(2, result.probedCandidates());
    }

    @Test
    void treatsMaximumBoundsAsExclusive() {
        BlockState air = block("air", "empty");
        BlockState stone = block("stone", "block");

        assertTrue(RegionPathPlanner.findNearestReachable(
            new Node(8, 64, 20),
            new RegionPathPlanner.Bounds(10, 64, 20, 12, 65, 21),
            (x, y, z) -> x == 12 && y == 63 && z == 20 ? stone : air,
            (start, goal) -> 3,
            64
        ).isEmpty());
    }

    @Test
    void rejectsBoundsAboveTheSharedVolumeLimit() {
        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new RegionPathPlanner.Bounds(0, 0, 0, 65, 65, 65)
        );

        assertTrue(error.getMessage().contains("262144"));
    }
}
