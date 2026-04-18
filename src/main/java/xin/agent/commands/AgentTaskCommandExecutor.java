package xin.agent.commands;

import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.command.Command;
import xin.bbtt.mcbot.command.CommandExecutor;
import xin.bbtt.mcbot.Utils;

import java.util.List;
import java.util.stream.Collectors;
import xin.agent.XinAgentPlugin;
import xin.agent.tasks.Task;

public class AgentTaskCommandExecutor extends CommandExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AgentTaskCommandExecutor.class);
    private static final List<String> SUB_COMMANDS = List.of("list", "add", "rm");

    @Override
    public void onCommand(Command command, String label, String[] args) {
        if (XinAgentPlugin.Instance == null || XinAgentPlugin.Instance.agentManager == null) {
            logger.error("AgentManager 未成功初始化！请检查开服/加载插件时的报错信息。");
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            List<Task> tasks = XinAgentPlugin.Instance.agentManager.getTaskManager().getTasks();
            if (tasks.isEmpty()) {
                logger.info("Task list is empty.");
            } else {
                logger.info("--- Task List ---");
                for (Task t : tasks) {
                    logger.info("{}", t.toString());
                }
                logger.info("-----------------");
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
            String desc = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            XinAgentPlugin.Instance.agentManager.getTaskManager().addTask(desc);
            logger.info("Added task: {}", desc);
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("rm")) {
            String id = args[1];
            if (XinAgentPlugin.Instance.agentManager.getTaskManager().removeTask(id)) {
                logger.info("Removed task: {}", id);
            } else {
                logger.info("Task not found: {}", id);
            }
        } else {
            logger.info("Usage: " + command.getUsage());
        }
    }

    @Override
    public List<String> onTabComplete(Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public AttributedStyle[] onHighlight(Command cmd, String label, String[] args) {
        return Utils.parseContainHighlight(args, SUB_COMMANDS, AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN), AttributedStyle.DEFAULT);
    }
}
