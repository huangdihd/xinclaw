package xin.agent.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.command.Command;
import xin.bbtt.mcbot.command.CommandExecutor;
import xin.bbtt.mcbot.Bot;
import xin.agent.XinAgentPlugin;

public class AgentClearCommandExecutor extends CommandExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AgentClearCommandExecutor.class);

    @Override
    public void onCommand(Command command, String label, String[] args) {
        if (XinAgentPlugin.Instance == null || XinAgentPlugin.Instance.agentManager == null) {
            logger.error("AgentManager 未成功初始化！");
            return;
        }

        XinAgentPlugin.Instance.agentManager.clearMemory();
        logger.info("Agent memory cleared by command.");
        Bot.Instance.sendChatMessage("AI 记忆已成功清除。");
    }
}
