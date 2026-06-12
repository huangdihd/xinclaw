package xin.claw.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.command.Command;
import xin.bbtt.mcbot.command.CommandExecutor;
import xin.claw.XinClawPlugin;

public class AgentClearCommandExecutor extends CommandExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AgentClearCommandExecutor.class);

    @Override
    public void onCommand(Command command, String label, String[] args) {
        if (XinClawPlugin.INSTANCE == null || XinClawPlugin.INSTANCE.agentManager == null) {
            logger.error("AgentManager 未成功初始化！");
            return;
        }

        boolean immediate = XinClawPlugin.INSTANCE.agentManager.clearMemory();
        if (immediate) {
            logger.info("AI 记忆已成功清除。");
        } else {
            logger.info("AI 正在思考中，记忆将在本轮对话结束后自动清除。");
        }

        int taskCount = XinClawPlugin.INSTANCE.agentManager.getTaskManager() != null
                ? XinClawPlugin.INSTANCE.agentManager.getTaskManager().getTasks().size() : 0;
        if (taskCount > 0) {
            logger.info("提醒：任务列表不会随记忆清除，当前仍有 {} 个任务。如需一并清空请执行 agenttask clear。", taskCount);
        }
    }
}
