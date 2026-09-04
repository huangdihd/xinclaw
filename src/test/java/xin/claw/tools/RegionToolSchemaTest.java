package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RegionToolSchemaTest {
    @Test
    void boundedSearchPublishesThreeVectorArrays() {
        ToolSpecification specification = ToolSpecifications.toolSpecificationsFrom(PerceptionTools.class)
            .stream()
            .filter(tool -> tool.name().equals("findSpecificBlocksInBounds"))
            .findFirst()
            .orElseThrow();

        Map<String, Map<String, Object>> properties = specification.parameters().properties();
        assertIntegerArray(properties.get("min"), properties.toString());
        assertIntegerArray(properties.get("max_exclusive"), properties.toString());
    }

    @Test
    void boundedNavigationPublishesThreeVectorArrays() {
        ToolSpecification specification = ToolSpecifications.toolSpecificationsFrom(MovementTools.class)
            .stream()
            .filter(tool -> tool.name().equals("pathfindToBounds"))
            .findFirst()
            .orElseThrow();

        Map<String, Map<String, Object>> properties = specification.parameters().properties();
        assertIntegerArray(properties.get("min"), properties.toString());
        assertIntegerArray(properties.get("max_exclusive"), properties.toString());
    }

    @Test
    void boundedPathPreviewPublishesThreeVectorArrays() {
        ToolSpecification specification = ToolSpecifications.toolSpecificationsFrom(MovementTools.class)
            .stream()
            .filter(tool -> tool.name().equals("previewPathToBounds"))
            .findFirst()
            .orElseThrow();

        Map<String, Map<String, Object>> properties = specification.parameters().properties();
        assertIntegerArray(properties.get("min"), properties.toString());
        assertIntegerArray(properties.get("max_exclusive"), properties.toString());
    }

    @Test
    void boundedMapPublishesSearchBoundsArrays() {
        ToolSpecification specification = ToolSpecifications.toolSpecificationsFrom(PerceptionTools.class)
            .stream()
            .filter(tool -> tool.name().equals("getAreaMapAt"))
            .findFirst()
            .orElseThrow();

        Map<String, Map<String, Object>> properties = specification.parameters().properties();
        assertIntegerArray(properties.get("min"), properties.toString());
        assertIntegerArray(properties.get("max_exclusive"), properties.toString());
        assertNotNull(properties.get("mapMinY"));
        assertNotNull(properties.get("mapMaxYExclusive"));
    }

    private static void assertIntegerArray(Map<String, Object> schema, String actualProperties) {
        assertNotNull(schema, actualProperties);
        assertEquals("array", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) schema.get("items");
        assertNotNull(items);
        assertEquals("integer", items.get("type"));
    }
}
