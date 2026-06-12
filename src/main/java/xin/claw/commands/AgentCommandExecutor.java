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

package xin.claw.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.command.Command;
import xin.bbtt.mcbot.command.CommandExecutor;
import xin.claw.XinClawPlugin;

public class AgentCommandExecutor extends CommandExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AgentCommandExecutor.class);

    @Override
    public void onCommand(Command command, String label, String[] args) {
        if (args.length == 0) {
            return;
        }

        if (XinClawPlugin.INSTANCE == null || XinClawPlugin.INSTANCE.agentManager == null) {
            logger.error("AgentManager 未成功初始化！请检查开服/加载插件时的报错信息。");
            return;
        }

        String message = String.join(" ", args);
        logger.info("Sending to agent: {}", message);

        XinClawPlugin.INSTANCE.agentManager.submitMessage(
                message,
                () -> logger.info("已打断AI当前的思考，开始处理新指令..."),
                response -> logger.info("Agent reply: {}", response));
    }
}
