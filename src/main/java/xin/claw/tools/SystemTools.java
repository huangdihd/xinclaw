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

package xin.claw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.Bot;
import xin.claw.CoordinateRange;
import xin.claw.PluginConfig;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemTools {
    private static final Logger logger = LoggerFactory.getLogger(SystemTools.class);
    private static final String NUMBER =
        "[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?";
    private static final Pattern LABELED_COMPONENT = Pattern.compile(
        "(?i)\\b([xyz])(?:\\s*[:=]\\s*|\\s+)(" + NUMBER + ")"
    );
    private static final Pattern TUPLE_COORDINATES = Pattern.compile(
        "(?<![\\w.])\\(?\\s*(" + NUMBER + ")\\s*[,;/]\\s*"
            + "(" + NUMBER + ")\\s*[,;/]\\s*(" + NUMBER + ")\\s*\\)?"
    );
    private static final Pattern BARE_COORDINATES = Pattern.compile(
        "^\\s*(" + NUMBER + ")\\s+(" + NUMBER + ")\\s+(" + NUMBER + ")\\s*$"
    );
    private static final Pattern HORIZONTAL_PAIR = Pattern.compile(
        "(?<![\\w.])\\(?\\s*(" + NUMBER + ")\\s*[,;/]\\s*"
            + "(" + NUMBER + ")\\s*\\)?"
    );
    private static final Pattern BARE_HORIZONTAL_PAIR = Pattern.compile(
        "^\\s*(" + NUMBER + ")\\s+(" + NUMBER + ")\\s*$"
    );
    private static final String COORDINATE_CUE =
        "(?:coords?|coordinates?|base|location|meet\\s+at|go\\s+to|i\\s+am\\s+at|i['’]?m\\s+at|at|坐标|位置|位于|我在|前往|去|到)";
    private static final Pattern CUE_COORDINATES = Pattern.compile(
        "(?i)" + COORDINATE_CUE + "\\D{0,24}(" + NUMBER + ")\\s+"
            + "(" + NUMBER + ")\\s+(" + NUMBER + ")"
    );
    private static final Pattern CUE_HORIZONTAL_PAIR = Pattern.compile(
        "(?i)" + COORDINATE_CUE + "\\D{0,24}(" + NUMBER + ")\\s+(" + NUMBER + ")"
    );
    private static final Pattern PUBLIC_CHAT_COMMAND = Pattern.compile(
        "(?i)(?:^|\\brun\\s+)(?:minecraft:)?(?:say|me|tellraw)\\s+"
    );
    private static final Pattern PUBLIC_COMMAND_COORDINATES = Pattern.compile(
        "(?<![\\w.])(" + NUMBER + ")[\\s,;/]+(" + NUMBER + ")"
            + "[\\s,;/]+(" + NUMBER + ")"
    );
    private static final Pattern PUBLIC_COMMAND_HORIZONTAL_PAIR = Pattern.compile(
        "(?<![\\w.])(" + NUMBER + ")[\\s,;/]+(" + NUMBER + ")"
    );
    private static final Pattern NUMBER_TOKEN = Pattern.compile(NUMBER);
    private final boolean commandExecutionEnabled;

    public SystemTools() {
        this(!Boolean.parseBoolean(System.getenv().getOrDefault("XINCLAW_DISABLE_SEND_COMMAND", "false")));
    }

    SystemTools(boolean commandExecutionEnabled) {
        this.commandExecutionEnabled = commandExecutionEnabled;
    }

    @Tool("在游戏内发送聊天消息。这将被服务器内的所有人看到。")
    public String sendChatMessage(@P("你要发送的文本内容") String message) {
        logger.info("[AI Tool Call] 调用了 sendChatMessage(message='{}')", message);
        if (containsForbiddenCoordinates(message, PluginConfig.publicChatForbiddenCoordinateRange)) {
            logger.warn("检测到受保护坐标范围内的位置，已拦截公屏消息。");
            return "发送失败：消息包含配置为禁止公开的坐标范围。";
        }
        if (Bot.INSTANCE == null) return "Bot实例未初始化。";
        
        sendChatMessageInChunks(message);
        return "消息已成功分段发送至游戏内聊天框。";
    }

    static boolean containsForbiddenCoordinates(String message, CoordinateRange range) {
        if (message == null || range == null) return false;
        return containsLabeledCoordinates(message, range)
            || containsCoordinateInRange(TUPLE_COORDINATES.matcher(message), range)
            || containsCoordinateInRange(BARE_COORDINATES.matcher(message), range)
            || containsCoordinateInRange(CUE_COORDINATES.matcher(message), range)
            || containsHorizontalCoordinateInRange(HORIZONTAL_PAIR.matcher(message), range)
            || containsHorizontalCoordinateInRange(BARE_HORIZONTAL_PAIR.matcher(message), range)
            || containsHorizontalCoordinateInRange(CUE_HORIZONTAL_PAIR.matcher(message), range);
    }

    private static boolean containsLabeledCoordinates(String message, CoordinateRange range) {
        Matcher matcher = LABELED_COMPONENT.matcher(message);
        java.util.List<Double> xs = new java.util.ArrayList<>();
        java.util.List<Double> ys = new java.util.ArrayList<>();
        java.util.List<Double> zs = new java.util.ArrayList<>();
        while (matcher.find()) {
            double value;
            try {
                value = Double.parseDouble(matcher.group(2));
            } catch (NumberFormatException ignored) {
                continue;
            }
            switch (matcher.group(1).toLowerCase(java.util.Locale.ROOT)) {
                case "x" -> xs.add(value);
                case "y" -> ys.add(value);
                case "z" -> zs.add(value);
                default -> { }
            }
        }
        if (xs.isEmpty() || zs.isEmpty()) return false;
        for (double x : xs) {
            for (double z : zs) {
                if (ys.isEmpty()) {
                    if (range.containsHorizontal(x, z)) return true;
                } else {
                    for (double y : ys) {
                        if (range.contains(x, y, z)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsCoordinateInRange(Matcher matcher, CoordinateRange range) {
        while (matcher.find()) {
            try {
                double x = Double.parseDouble(matcher.group(1));
                double y = Double.parseDouble(matcher.group(2));
                double z = Double.parseDouble(matcher.group(3));
                if (range.contains(x, y, z)) return true;
            } catch (NumberFormatException ignored) {
                // The regex accepts only decimal numbers; keep scanning if a
                // value still exceeds Double's parser limits.
            }
        }
        return false;
    }

    private static boolean containsHorizontalCoordinateInRange(
            Matcher matcher, CoordinateRange range) {
        while (matcher.find()) {
            try {
                double x = Double.parseDouble(matcher.group(1));
                double z = Double.parseDouble(matcher.group(2));
                if (range.containsHorizontal(x, z)) return true;
            } catch (NumberFormatException ignored) {
                // Keep scanning if a decimal still exceeds Double's parser limits.
            }
        }
        return false;
    }

    private void sendChatMessageInChunks(String text) {
        int byteLimit = 90;
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            
            if (currentBytes + charBytes > byteLimit) {
                Bot.INSTANCE.sendChatMessage(currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            
            currentChunk.append(c);
            currentBytes += charBytes;
        }
        
        if (currentChunk.length() > 0) {
            Bot.INSTANCE.sendChatMessage(currentChunk.toString());
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
        if (!commandExecutionEnabled) {
            return "Command execution is disabled for this benchmark.";
        }
        String publicText = publicChatCommandText(command);
        if (containsForbiddenCoordinatesInPublicCommand(
                publicText, PluginConfig.publicChatForbiddenCoordinateRange)) {
            logger.warn("检测到受保护坐标范围内的位置，已拦截公屏指令。");
            return "执行失败：公屏指令包含配置为禁止公开的坐标范围。";
        }
        if (Bot.INSTANCE == null) return "Bot实例未初始化。";
        
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
        
        Bot.INSTANCE.sendCommand(command);
        return "指令执行请求已发送。";
    }

    private static String publicChatCommandText(String command) {
        if (command == null) return null;
        String trimmed = command.stripLeading();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1).stripLeading();
        Matcher matcher = PUBLIC_CHAT_COMMAND.matcher(trimmed);
        return matcher.find() ? trimmed.substring(matcher.end()) : null;
    }

    private static boolean containsForbiddenCoordinatesInPublicCommand(
            String publicText, CoordinateRange range) {
        if (containsForbiddenCoordinates(publicText, range)) return true;
        if (publicText == null || range == null) return false;
        return containsCoordinateInRange(PUBLIC_COMMAND_COORDINATES.matcher(publicText), range)
            || containsHorizontalCoordinateInRange(
                PUBLIC_COMMAND_HORIZONTAL_PAIR.matcher(publicText), range)
            || containsAnyPublicNumberPairInRange(publicText, range);
    }

    private static boolean containsAnyPublicNumberPairInRange(
            String publicText, CoordinateRange range) {
        java.util.List<Double> numbers = new java.util.ArrayList<>();
        Matcher matcher = NUMBER_TOKEN.matcher(publicText);
        while (matcher.find()) {
            try {
                numbers.add(Double.parseDouble(matcher.group()));
            } catch (NumberFormatException ignored) {
                // Skip numbers outside Double's parser range.
            }
        }
        for (int i = 0; i < numbers.size(); i++) {
            for (int j = i + 1; j < numbers.size(); j++) {
                double first = numbers.get(i);
                double second = numbers.get(j);
                if (range.containsHorizontal(first, second)
                        || range.containsHorizontal(second, first)) return true;
            }
        }
        return false;
    }

    private void sendTellInChunks(String cmdType, String recipient, String text) {
        int byteLimit = 80; 
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            
            if (currentBytes + charBytes > byteLimit) {
                Bot.INSTANCE.sendCommand(cmdType + " " + recipient + " " + currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            
            currentChunk.append(c);
            currentBytes += charBytes;
        }
        
        if (currentChunk.length() > 0) {
            Bot.INSTANCE.sendCommand(cmdType + " " + recipient + " " + currentChunk.toString());
        }
    }

    @Tool("获取指定指令的自动补全建议。用于查询指令的具体参数选项。")
    public String getCommandCompletions(@P("部分输入的指令文本(不带'/')") String partialCommand) {
        logger.info("[AI Tool Call] 调用了 getCommandCompletions(partialCommand='{}')", partialCommand);
        if (Bot.INSTANCE == null || Bot.INSTANCE.getPluginManager() == null) return "Bot实例未初始化。";
        
        java.util.List<String> completions = Bot.INSTANCE.getPluginManager().commands().callComplete(partialCommand);
        if (completions == null || completions.isEmpty()) {
            return "没有找到该指令的补全建议。";
        }
        
        return "补全建议: " + String.join(", ", completions);
    }
}
