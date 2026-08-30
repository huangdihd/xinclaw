package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import xin.bbtt.Block.BlockState;

final class PerceptionToolStateOutputTest {
    private static BlockState state(String name, Map<String, String> properties) {
        return new BlockState(name, 0, properties, "block", 1.0, true, "wood");
    }

    @Test
    void formatStateRendersPropertiesInInsertionOrder() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("facing", "north");
        properties.put("half", "lower");
        properties.put("open", "false");
        assertEquals(
            "[facing=north,half=lower,open=false]",
            PerceptionTools.formatState(state("jungle_door", properties))
        );
    }

    @Test
    void formatStateReturnsEmptyStringForPropertylessBlocks() {
        assertEquals("", PerceptionTools.formatState(state("smooth_sandstone", Map.of())));
        assertEquals("", PerceptionTools.formatState(null));
    }

    @Test
    void searchesDocumentOpenStateAndDoorWorkflow() {
        ToolSpecification radiusSearch = tool("findSpecificBlocks", PerceptionTools.class);
        assertTrue(radiusSearch.description().contains("open"), radiusSearch.description());
        assertTrue(radiusSearch.description().contains("interactBlock"), radiusSearch.description());

        ToolSpecification boundedSearch = tool("findSpecificBlocksInBounds", PerceptionTools.class);
        assertTrue(boundedSearch.description().contains("open"), boundedSearch.description());
        assertTrue(boundedSearch.description().contains("interactBlock"), boundedSearch.description());
    }

    @Test
    void interactBlockDocumentsDoorAndButtonUsage() {
        ToolSpecification interact = tool("interactBlock", ActionTools.class);
        assertTrue(interact.description().contains("门"), interact.description());
        assertTrue(interact.description().contains("按钮"), interact.description());
        assertTrue(interact.description().contains("拉杆"), interact.description());
    }

    private static ToolSpecification tool(String name, Class<?> clazz) {
        return ToolSpecifications.toolSpecificationsFrom(clazz)
            .stream()
            .filter(candidate -> candidate.name().equals(name))
            .findFirst()
            .orElseThrow();
    }
}
