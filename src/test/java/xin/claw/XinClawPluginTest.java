package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class XinClawPluginTest {
    @Test
    void teleportAgentNotificationsCanBeSuppressedDuringControlledReset() {
        XinClawPlugin plugin = new XinClawPlugin();

        assertFalse(plugin.isTeleportAgentNotificationsSuppressed());
        plugin.setTeleportAgentNotificationsSuppressed(true);
        assertTrue(plugin.isTeleportAgentNotificationsSuppressed());
        plugin.setTeleportAgentNotificationsSuppressed(false);
        assertFalse(plugin.isTeleportAgentNotificationsSuppressed());
    }
}
