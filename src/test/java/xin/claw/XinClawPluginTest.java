package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.List;
import org.joml.Vector3i;
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

    @Test
    void storedMovementGoalSurvivesRuntimeGoalClearing() {
        Vector3i stored = new Vector3i(12, 70, 34);
        Vector3i runtime = new Vector3i(13, 70, 34);

        assertSame(runtime, XinClawPlugin.effectiveMovementGoal(runtime, stored));
        assertSame(stored, XinClawPlugin.effectiveMovementGoal(null, stored));
        assertNull(XinClawPlugin.effectiveMovementGoal(null, null));
    }

    @Test
    void distinguishesAnInternalOnlyContinuationFromRealPendingWork() {
        Task internal = new Task("[SYSTEM_CONTINUATION] waypoint");
        internal.setStatus(Task.Status.IN_PROGRESS);
        Task real = new Task("enter building");
        real.setStatus(Task.Status.IN_PROGRESS);

        assertTrue(XinClawPlugin.onlyPendingTaskIsImplicit(
            List.of(internal), internal.getId(), true));
        assertFalse(XinClawPlugin.onlyPendingTaskIsImplicit(
            List.of(internal, real), internal.getId(), true));
        assertFalse(XinClawPlugin.onlyPendingTaskIsImplicit(
            List.of(internal), internal.getId(), false));
    }

    @Test
    void invalidationClearsContinuationStateAndRejectsCapturedTick() {
        XinClawPlugin plugin = new XinClawPlugin();
        plugin.currentMovementTaskId = "internal";
        plugin.currentMovementTaskIsImplicit = true;
        plugin.currentMovementGoal = new Vector3i(12, 70, 34);
        long captured = plugin.taskLoopGeneration();

        plugin.invalidateTaskLoopWork();

        assertFalse(plugin.isTaskLoopGenerationCurrent(captured));
        assertNull(plugin.currentMovementTaskId);
        assertFalse(plugin.currentMovementTaskIsImplicit);
        assertNull(plugin.currentMovementGoal);
    }

    @Test
    void taskLoopCanBeSuspendedAcrossEpisodeCleanup() {
        XinClawPlugin plugin = new XinClawPlugin();
        long before = plugin.taskLoopGeneration();

        plugin.suspendTaskLoopWork();
        assertTrue(plugin.isTaskLoopWorkSuppressed());
        assertFalse(plugin.isTaskLoopGenerationCurrent(before));

        plugin.resumeTaskLoopWork();
        assertFalse(plugin.isTaskLoopWorkSuppressed());
    }
}
