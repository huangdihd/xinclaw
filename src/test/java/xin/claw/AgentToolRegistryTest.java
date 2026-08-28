package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class AgentToolRegistryTest {
    @Test
    void externalToolsAreAppendedWithoutReplacingDefaults() {
        Object defaultTool = new Object();
        Object externalTool = new Object();
        AgentToolRegistry registry = new AgentToolRegistry(List.of(defaultTool));

        assertTrue(registry.registerExternal(externalTool));

        assertEquals(List.of(defaultTool, externalTool), registry.snapshot());
    }

    @Test
    void duplicateIdentityRegistrationDoesNotDuplicateTool() {
        Object externalTool = new Object();
        AgentToolRegistry registry = new AgentToolRegistry(List.of());

        assertTrue(registry.registerExternal(externalTool));
        assertFalse(registry.registerExternal(externalTool));

        assertEquals(List.of(externalTool), registry.snapshot());
    }

    @Test
    void failedRegistrationRebuildRollsBackAndCanBeRetried() {
        Object externalTool = new Object();
        AgentToolRegistry registry = new AgentToolRegistry(List.of());

        assertThrows(
            IllegalStateException.class,
            () -> registry.registerExternal(
                externalTool,
                () -> { throw new IllegalStateException("rebuild failed"); }
            )
        );
        assertTrue(registry.snapshot().isEmpty());

        assertTrue(registry.registerExternal(externalTool, () -> {}));
        assertEquals(List.of(externalTool), registry.snapshot());
    }

    @Test
    void failedUnregistrationRebuildRestoresTheTool() {
        Object first = new Object();
        Object externalTool = new Object();
        Object last = new Object();
        AgentToolRegistry registry = new AgentToolRegistry(List.of());
        registry.registerExternal(first);
        registry.registerExternal(externalTool);
        registry.registerExternal(last);

        assertThrows(
            IllegalStateException.class,
            () -> registry.unregisterExternal(
                externalTool,
                () -> { throw new IllegalStateException("rebuild failed"); }
            )
        );
        assertEquals(List.of(first, externalTool, last), registry.snapshot());

        assertTrue(registry.unregisterExternal(externalTool, () -> {}));
        assertEquals(List.of(first, last), registry.snapshot());
    }

    @Test
    void agentManagerPublishesExternalToolLifecycleApi() throws Exception {
        assertEquals(
            void.class,
            AgentManager.class.getMethod("registerExternalTool", Object.class).getReturnType()
        );
        assertEquals(
            void.class,
            AgentManager.class.getMethod("unregisterExternalTool", Object.class).getReturnType()
        );
    }

    @Test
    void unregisterRemovesOnlyTheMatchingExternalIdentity() {
        Object defaultTool = new Object();
        Object first = new Object();
        Object equalButDistinct = new String("tool");
        Object secondEqualButDistinct = new String("tool");
        AgentToolRegistry registry = new AgentToolRegistry(List.of(defaultTool));
        registry.registerExternal(first);
        registry.registerExternal(equalButDistinct);
        registry.registerExternal(secondEqualButDistinct);

        assertTrue(registry.unregisterExternal(equalButDistinct));

        assertEquals(List.of(defaultTool, first, secondEqualButDistinct), registry.snapshot());
        assertFalse(registry.unregisterExternal(equalButDistinct));
    }
}
