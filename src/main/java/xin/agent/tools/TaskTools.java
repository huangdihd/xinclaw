package xin.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.agent.tasks.Task;
import xin.agent.tasks.TaskManager;

import java.util.List;
import java.util.stream.Collectors;

public class TaskTools {
    private static final Logger logger = LoggerFactory.getLogger(TaskTools.class);
    private final TaskManager taskManager;

    public TaskTools(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Tool("列出机器人当前所有的任务（包括待办、进行中和已完成）。")
    public String listTasks() {
        logger.info("[AI Tool Call] 调用了 listTasks()");
        List<Task> tasks = taskManager.getTasks();
        if (tasks.isEmpty()) return "当前没有任何任务。";
        
        return "当前任务列表:\n" + tasks.stream()
                .map(Task::toString)
                .collect(Collectors.joining("\n"));
    }

    @Tool("添加一个新的长期或短期任务到任务列表。")
    public String addTask(@P("任务的具体描述") String description) {
        logger.info("[AI Tool Call] 调用了 addTask(desc={})", description);
        taskManager.addTask(description);
        return "成功添加任务: " + description;
    }

    @Tool("根据 ID 删除指定的任务。")
    public String removeTask(@P("任务的 ID (如: 4a2b1c3d)") String id) {
        logger.info("[AI Tool Call] 调用了 removeTask(id={})", id);
        if (taskManager.removeTask(id)) {
            return "成功删除任务 ID: " + id;
        }
        return "未找到 ID 为 " + id + " 的任务。";
    }

    @Tool("更新指定任务的状态。")
    public String updateTaskStatus(
            @P("任务的 ID") String id, 
            @P("状态，可选值: TODO (待办), IN_PROGRESS (进行中), DONE (已完成)") String statusStr) {
        logger.info("[AI Tool Call] 调用了 updateTaskStatus(id={}, status={})", id, statusStr);
        try {
            Task.Status status = Task.Status.valueOf(statusStr.toUpperCase());
            if (taskManager.updateTaskStatus(id, status)) {
                return "任务 " + id + " 状态已更新为 " + status;
            }
        } catch (IllegalArgumentException e) {
            return "无效的状态值。";
        }
        return "未找到 ID 为 " + id + " 的任务。";
    }
}
