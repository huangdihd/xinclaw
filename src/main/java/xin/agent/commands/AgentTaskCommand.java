package xin.agent.commands;

import xin.bbtt.mcbot.command.Command;

public class AgentTaskCommand extends Command {
    @Override
    public String getName() {
        return "agenttask";
    }

    @Override
    public String[] getAliases() {
        return new String[] {"aitask", "bottask"};
    }

    @Override
    public String getDescription() {
        return "Manage AI Agent tasks";
    }

    @Override
    public String getUsage() {
        return "agenttask [list|add <desc>|rm <id>|clear]";
    }
}
