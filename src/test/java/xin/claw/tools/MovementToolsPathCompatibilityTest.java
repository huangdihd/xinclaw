package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import xin.bbtt.pathfinding.Node;

final class MovementToolsPathCompatibilityTest {
    static final class RuntimePathStep {
        private final Node node;

        RuntimePathStep(Node node) {
            this.node = node;
        }

        public Node getNode() {
            return node;
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
}
