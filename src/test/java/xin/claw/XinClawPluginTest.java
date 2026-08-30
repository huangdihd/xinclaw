package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.List;
import xin.claw.tasks.Task;

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

    @Test
    void autonomousLoopTreatsTodoAndInProgressAsPendingWork() {
        Task todo = new Task("todo");
        Task active = new Task("active");
        active.setStatus(Task.Status.IN_PROGRESS);
        Task done = new Task("done");
        done.setStatus(Task.Status.DONE);

        assertTrue(XinClawPlugin.hasPendingTasks(List.of(todo)));
        assertTrue(XinClawPlugin.hasPendingTasks(List.of(active)));
        assertFalse(XinClawPlugin.hasPendingTasks(List.of(done)));
        assertFalse(XinClawPlugin.hasPendingTasks(List.of()));
    }
}
