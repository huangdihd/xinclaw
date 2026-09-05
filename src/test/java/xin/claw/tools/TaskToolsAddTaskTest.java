package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xin.claw.AgentManager;
import xin.claw.tasks.Task;
import xin.claw.tasks.TaskManager;

final class TaskToolsAddTaskTest {
    @Test
    void addTaskReturnsThePersistedTaskIdForImmediateReuse(@TempDir Path directory) throws Exception {
        TaskManager manager = new TaskManager(directory.resolve("tasks.json").toFile());
        TaskTools tools = new TaskTools(manager);

        String output = tools.addTask("进入瞭望塔");

        Task created = manager.getTasks().get(0);
        assertTrue(output.contains(created.getId()), output);
        assertTrue(output.contains("TODO"), output);
        assertTrue(output.contains("进入瞭望塔"), output);
        assertTrue(output.contains("updateTaskStatus"), output);
        assertEquals(8, created.getId().length());

        Tool annotation = TaskTools.class.getMethod("addTask", String.class).getAnnotation(Tool.class);
        assertNotNull(annotation);
        assertTrue(String.join("\n", annotation.value()).contains("返回"));
        assertTrue(String.join("\n", annotation.value()).contains("ID"));

        String prompt = AgentManager.configuredSystemPrompt();
        assertTrue(prompt.contains("addTask 返回"), prompt);
        assertTrue(prompt.contains("禁止猜测"), prompt);
    }
}
