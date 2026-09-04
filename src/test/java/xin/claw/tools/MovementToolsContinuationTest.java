package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xin.claw.tasks.Task;
import xin.claw.tasks.TaskManager;

final class MovementToolsContinuationTest {
    @Test
    void blankTaskIdCreatesInternalInProgressContinuation(@TempDir Path directory) {
        TaskManager manager = new TaskManager(directory.resolve("tasks.json").toFile());

        MovementTools.MovementTaskBinding binding = MovementTools.bindMovementTask(
            manager, "", 12.0, 70.0, 34.0
        );

        assertTrue(binding.implicit());
        assertFalse(binding.taskId().isBlank());
        assertEquals(1, manager.getTasks().size());
        Task task = manager.getTasks().get(0);
        assertEquals(binding.taskId(), task.getId());
        assertEquals(Task.Status.IN_PROGRESS, task.getStatus());
        assertTrue(task.getDescription().contains("12,70,34"), task.getDescription());
    }

    @Test
    void explicitTaskIdDoesNotCreateInternalTask(@TempDir Path directory) {
        TaskManager manager = new TaskManager(directory.resolve("tasks.json").toFile());

        MovementTools.MovementTaskBinding binding = MovementTools.bindMovementTask(
            manager, "abc12345", 12.0, 70.0, 34.0
        );

        assertFalse(binding.implicit());
        assertEquals("abc12345", binding.taskId());
        assertTrue(manager.getTasks().isEmpty());
    }

    @Test
    void completedInternalContinuationIsRemoved(@TempDir Path directory) {
        TaskManager manager = new TaskManager(directory.resolve("tasks.json").toFile());
        MovementTools.MovementTaskBinding binding = MovementTools.bindMovementTask(
            manager, null, 12.0, 70.0, 34.0
        );

        assertTrue(MovementTools.completeMovementTask(manager, binding.taskId(), true));
        assertTrue(manager.getTasks().isEmpty());
    }
}
