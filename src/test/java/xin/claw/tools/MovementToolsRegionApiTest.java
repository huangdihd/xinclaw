package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.Tool;
import java.lang.reflect.Method;
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
        Method preview = MovementTools.class.getMethod(
            "previewPathToBounds",
            int[].class, int[].class
        );
        Tool previewTool = preview.getAnnotation(Tool.class);
        assertNotNull(previewTool);
        String previewDescription = String.join("\n", previewTool.value());
        assertTrue(previewDescription.contains("不会移动"));
        assertTrue(previewDescription.contains("寻路节点"));

        assertNotNull(MovementTools.class.getMethod(
            "walkTo", double.class, double.class, double.class
        ));
        assertNotNull(MovementTools.class.getMethod(
            "pathfindTo", double.class, double.class, double.class, String.class
        ));
    }
}
