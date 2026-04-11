package xin.agent;

import xin.bbtt.mcbot.command.Command;

public class AgentCommand extends Command {
    
    @Override
    public String getName() {
        return "agent";
    }

    @Override
    public String[] getAliases() {
        return new String[] {"ai", "bot"};
    }

    @Override
    public String getDescription() {
        return "Chat with the Langchain4j AI Agent";
    }

    @Override
    public String getUsage() {
        return "agent <message>";
    }
}
