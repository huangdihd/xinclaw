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

package xin.agent.commands;

import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.command.Command;
import xin.bbtt.mcbot.command.CommandExecutor;
import xin.bbtt.mcbot.Bot;
import xin.bbtt.mcbot.Utils;

import java.util.List;
import java.util.stream.Collectors;
import xin.agent.XinAgentPlugin;

public class AgentCommandExecutor extends CommandExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AgentCommandExecutor.class);
    private static final List<String> SUB_COMMANDS = List.of("clear");

    @Override
    public void onCommand(Command command, String label, String[] args) {
        if (args.length == 0) {
            return;
        }

        if (XinAgentPlugin.Instance == null || XinAgentPlugin.Instance.agentManager == null) {
            logger.error("AgentManager 未成功初始化！请检查开服/加载插件时的报错信息。");
            return;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            XinAgentPlugin.Instance.agentManager.clearMemory();
            logger.info("Agent memory cleared by command.");
            Bot.Instance.sendChatMessage("AI 记忆已成功清除。");
            return;
        }

        String message = String.join(" ", args);
        logger.info("Sending to agent: {}", message);
        
        // 使用托管的线程池执行任务
        if (XinAgentPlugin.Instance.executorService == null || XinAgentPlugin.Instance.executorService.isShutdown()) {
            return;
        }

        XinAgentPlugin.Instance.executorService.submit(() -> {
            try {
                String response = XinAgentPlugin.Instance.agentManager.processMessage(message);
                logger.info("Agent reply: {}", response);
                
                String[] lines = response.split("\\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        sendInChunks(line.trim());
                    }
                }
            } catch (Exception e) {
                logger.error("Error while chatting with agent", e);
            }
        });
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

    private void sendInChunks(String text) {
        int byteLimit = 90; // Strictly under 100 bytes
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

            if (currentBytes + charBytes > byteLimit) {
                // 原本是 Bot.Instance.sendChatMessage(currentChunk.toString());
                // 现在改为只在控制台输出
                logger.info("[AI Reply] {}", currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
            }

            currentChunk.append(c);
            currentBytes += charBytes;
        }

        if (currentChunk.length() > 0) {
            logger.info("[AI Reply] {}", currentChunk.toString());
        }
    }
}
