package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import xin.bbtt.pathfinding.Node;

final class MovementToolsPathCompatibilityTest {
    enum TestMovementType { WALK, CLIMB }

    static final class RuntimePathStep {
        private final Node node;
        private final TestMovementType type;

        RuntimePathStep(Node node) {
            this(node, TestMovementType.WALK);
        }

        RuntimePathStep(Node node, TestMovementType type) {
            this.node = node;
            this.type = type;
        }

        public Node getNode() {
            return node;
        }

        public TestMovementType getType() {
            return type;
        }
    }

    @Test
    void extractsEndpointFromLegacyNodePaths() {
        assertEquals(
            new Node(3, 64, 5),
            MovementTools.pathEndpoint(List.of(new Node(1, 64, 5), new Node(3, 64, 5)))
        );
    }

    @Test
    void extractsEndpointFromRuntimePathStepPaths() {
        assertEquals(
            new Node(3, 64, 5),
            MovementTools.pathEndpoint(List.of(
                new RuntimePathStep(new Node(1, 64, 5)),
                new RuntimePathStep(new Node(3, 64, 5))
            ))
        );
    }

    @Test
    void formatsLegacyPathNodesWithoutStartingMovement() {
        RegionPathPlanner.Result target = new RegionPathPlanner.Result(
            new Node(3, 64, 5), 3, 7, 2
        );

        String output = MovementTools.formatPathPreview(
            target,
            List.of(new Node(1, 64, 5), new Node(2, 64, 5), new Node(3, 64, 5))
        );

        assertTrue(output.contains("不会移动"));
        assertTrue(output.contains("selected_target=(3,64,5)"));
        assertTrue(output.contains("total_nodes=3"));
        assertTrue(output.contains("0:WALK@(1,64,5)"));
        assertTrue(output.contains("2:WALK@(3,64,5)"));
        assertTrue(output.contains("standable_candidates=7"));
        assertTrue(output.contains("probed_candidates=2"));
    }

    @Test
    void formatsRuntimePathStepMovementTypes() {
        RegionPathPlanner.Result target = new RegionPathPlanner.Result(
            new Node(2, 65, 5), 2, 1, 1
        );

        String output = MovementTools.formatPathPreview(
            target,
            List.of(
                new RuntimePathStep(new Node(1, 64, 5), TestMovementType.WALK),
                new RuntimePathStep(new Node(2, 65, 5), TestMovementType.CLIMB)
            )
        );

        assertTrue(output.contains("0:WALK@(1,64,5)"));
        assertTrue(output.contains("1:CLIMB@(2,65,5)"));
    }

    @Test
    void truncatesOnlyTheMiddleOfLongPathPreviews() {
        List<Node> path = new ArrayList<>();
        for (int x = 0; x < 100; x++) path.add(new Node(x, 64, 5));
        RegionPathPlanner.Result target = new RegionPathPlanner.Result(
            new Node(99, 64, 5), 100, 1, 1
        );

        String output = MovementTools.formatPathPreview(target, path);

        assertTrue(output.contains("omitted_middle_nodes=4"));
        assertTrue(output.contains("0:WALK@(0,64,5)"));
        assertTrue(output.contains("99:WALK@(99,64,5)"));
    }
}
