/*
 *   Copyright (C) 2026 huangdihd
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
