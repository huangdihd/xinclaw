package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;

final class MovementToolsRegionApiTest {
    @Test
    void publishesRegionNavigationWithoutRemovingLegacyMovementTools() throws Exception {
        assertNotNull(MovementTools.class.getMethod(
            "findReachablePointInBounds",
            int[].class, int[].class
        ).getAnnotation(Tool.class));
        assertNotNull(MovementTools.class.getMethod(
            "pathfindToBounds",
            int[].class, int[].class, String.class
        ).getAnnotation(Tool.class));

        assertNotNull(MovementTools.class.getMethod(
            "walkTo", double.class, double.class, double.class
        ));
        assertNotNull(MovementTools.class.getMethod(
            "pathfindTo", double.class, double.class, double.class, String.class
        ));
    }
}
