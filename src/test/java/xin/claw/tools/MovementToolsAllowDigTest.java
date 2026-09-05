package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Navigation must be dig-free unless the agent explicitly opts in:
 * the benchmark forbids mining through closed-door buildings, so every
 * navigation tool publishes allowDig (optional, default false).
 */
final class MovementToolsAllowDigTest {
    private static final List<String> NAVIGATION_TOOLS = List.of(
        "pathfindTo", "previewPathTo", "pathfindToBounds", "previewPathToBounds");


    @Test
    void everyNavigationToolPublishesOptionalBooleanAllowDig() {
        for (String tool : NAVIGATION_TOOLS) {
            ToolSpecification specification = toolSpecification(tool);
            Map<String, Map<String, Object>> properties = specification.parameters().properties();
            Map<String, Object> allowDig = properties.get("allowDig");
            assertNotNull(allowDig, tool + " must publish allowDig; got " + properties.keySet());
            assertEquals("boolean", allowDig.get("type"), tool + " allowDig type");

            List<?> required = requiredParameters(specification);
            assertTrue(required == null || required.stream().noneMatch("allowDig"::equals),
                tool + " must keep allowDig optional so omitting it defaults to no digging");
        }
    }

    @Test
    void navigationToolDescriptionsStateDiggingIsOffByDefault() {
        for (String tool : NAVIGATION_TOOLS) {
            String description = toolSpecification(tool).description();
            assertTrue(description.contains("默认不挖掘"),
                tool + " must say digging is disabled by default: " + description);
            assertTrue(description.contains("allowDig"),
                tool + " must reference the allowDig parameter: " + description);
        }
    }

    @Test
    void pathfindToNoLongerPromisesAutomaticDigging() {
        String description = toolSpecification("pathfindTo").description();
        assertFalse(description.contains("自动开路(挖方块)"),
            "old always-digs promise must be removed: " + description);
    }


    @Test
    void resolveAllowDigTreatsOnlyExplicitTrueAsEnabled() {
        assertFalse(MovementTools.resolveAllowDig(null));
        assertFalse(MovementTools.resolveAllowDig(false));
        assertTrue(MovementTools.resolveAllowDig(true));
    }

    private static ToolSpecification toolSpecification(String name) {
        return ToolSpecifications.toolSpecificationsFrom(MovementTools.class).stream()
            .filter(tool -> tool.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing tool " + name));
    }

    @SuppressWarnings("unchecked")
    private static List<?> requiredParameters(ToolSpecification specification) {
        try {
            Object parameters = specification.parameters();
            return (List<?>) parameters.getClass().getMethod("required").invoke(parameters);
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }
}
