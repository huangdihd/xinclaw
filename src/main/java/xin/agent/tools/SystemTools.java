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

package xin.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.Bot;

import java.nio.charset.StandardCharsets;

public class SystemTools {
    private static final Logger logger = LoggerFactory.getLogger(SystemTools.class);

    @Tool("在游戏内发送聊天消息。这将被服务器内的所有人看到。")
    public String sendChatMessage(@P("你要发送的文本内容") String message) {
        logger.info("[AI Tool Call] 调用了 sendChatMessage(message='{}')", message);
        if (Bot.Instance == null) return "Bot实例未初始化。";
        sendChatMessageInChunks(message);
        return "消息已成功分段发送至游戏内聊天框。";
    }

    private void sendChatMessageInChunks(String text) {
        int byteLimit = 90;
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            
            if (currentBytes + charBytes > byteLimit) {
                Bot.Instance.sendChatMessage(currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            
            currentChunk.append(c);
            currentBytes += charBytes;
        }
        
        if (currentChunk.length() > 0) {
            Bot.Instance.sendChatMessage(currentChunk.toString());
        }
    }

    @Tool("在游戏内执行指令。注意：不需要在开头加上'/'，系统会自动加上'/'。\n" +
          "可用指令示例：\n" +
          "- kill: 自杀\n" +
          "- tell <玩家名> <内容>: 私聊\n" +
          "- chat <玩家名>: 屏蔽/解除屏蔽某玩家\n" +
          "- sc list: 查看屏蔽玩家列表\n" +
          "- stat <玩家名>: 查询玩家基础信息")
    public String sendCommand(@P("你要执行的指令文本(不带'/')") String command) {
        logger.info("[AI Tool Call] 调用了 sendCommand(command='{}')", command);
        if (Bot.Instance == null) return "Bot实例未初始化。";
        
        if (command.startsWith("tell ") || command.startsWith("msg ") || command.startsWith("w ")) {
            String[] parts = command.split(" ", 3);
            if (parts.length == 3) {
                String cmdType = parts[0];
                String recipient = parts[1];
                String text = parts[2];
                sendTellInChunks(cmdType, recipient, text);
                return "指令已分段执行。";
            }
        }
        
        Bot.Instance.sendCommand(command);
        return "指令执行请求已发送。";
    }

    private void sendTellInChunks(String cmdType, String recipient, String text) {
        int byteLimit = 80; 
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            
            if (currentBytes + charBytes > byteLimit) {
                Bot.Instance.sendCommand(cmdType + " " + recipient + " " + currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            
            currentChunk.append(c);
            currentBytes += charBytes;
        }
        
        if (currentChunk.length() > 0) {
            Bot.Instance.sendCommand(cmdType + " " + recipient + " " + currentChunk.toString());
        }
    }

    @Tool("获取指定指令的自动补全建议。用于查询指令的具体参数选项。")
    public String getCommandCompletions(@P("部分输入的指令文本(不带'/')") String partialCommand) {
        logger.info("[AI Tool Call] 调用了 getCommandCompletions(partialCommand='{}')", partialCommand);
        if (Bot.Instance == null || Bot.Instance.getPluginManager() == null) return "Bot实例未初始化。";
        
        java.util.List<String> completions = Bot.Instance.getPluginManager().commands().callComplete(partialCommand);
        if (completions == null || completions.isEmpty()) {
            return "没有找到该指令的补全建议。";
        }
        
        return "补全建议: " + String.join(", ", completions);
    }
}
