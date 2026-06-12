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

package xin.claw.listeners;

import xin.bbtt.mcbot.Bot;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.PrivateChatEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import xin.claw.XinClawPlugin;
import xin.claw.PluginConfig;

public class PrivateMessageListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(PrivateMessageListener.class);

    @EventHandler
    public void onPrivateChat(PrivateChatEvent event) {
        if (Bot.INSTANCE == null || Bot.INSTANCE.getConfig() == null) return;
        
        String owner = Bot.INSTANCE.getConfig().getConfigData().getOwner();
        if (owner == null || owner.isEmpty()) return;
        
        String senderName = event.getSender().getName();
        
        if (!senderName.equalsIgnoreCase(owner)) {
            return;
        }

        String message = event.getMessage();
        
        // 屏蔽词检查 (等于模式)
        if (PluginConfig.privateMessageBlacklist.contains(message.trim().toLowerCase())) {
            logger.info("Ignoring private message from owner because it is in the blacklist: {}", message);
            return;
        }

        logger.info("Received private message from owner {}: {}", senderName, message);

        if (XinClawPlugin.INSTANCE == null || XinClawPlugin.INSTANCE.agentManager == null) {
            return;
        }

        XinClawPlugin.INSTANCE.agentManager.submitMessage(
                message,
                () -> sendInChunks(senderName, "已打断AI当前的思考，开始处理新指令..."),
                response -> {
                    logger.info("AI reply to owner: {}", response);
                    for (String line : response.split("\\r?\\n")) {
                        if (!line.trim().isEmpty()) {
                            sendInChunks(senderName, line.trim());
                        }
                    }
                });
    }

    private void sendInChunks(String recipient, String text) {
        int byteLimit = 80;
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            
            if (currentBytes + charBytes > byteLimit) {
                Bot.INSTANCE.sendCommand("tell " + recipient + " " + currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            
            currentChunk.append(c);
            currentBytes += charBytes;
        }
        
        if (currentChunk.length() > 0) {
            Bot.INSTANCE.sendCommand("tell " + recipient + " " + currentChunk.toString());
        }
    }
}
