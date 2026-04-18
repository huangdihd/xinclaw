package xin.agent.commands;

import xin.bbtt.mcbot.command.Command;

public class AgentClearCommand extends Command {
    @Override
    public String getName() {
        return "agentclear";
    }

    @Override
    public String[] getAliases() {
        return new String[] {"aiclear", "botclear"};
    }

    @Override
    public String getDescription() {
        return "Clear AI Agent memory";
    }

    @Override
    public String getUsage() {
        return "agentclear";
    }
}
