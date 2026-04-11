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

import xin.bbtt.mcbot.Bot;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.PrivateChatEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class PrivateMessageListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(PrivateMessageListener.class);

    @EventHandler
    public void onPrivateChat(PrivateChatEvent event) {
        if (Bot.Instance == null || Bot.Instance.getConfig() == null) return;
        
        String owner = Bot.Instance.getConfig().getConfigData().getOwner();
        if (owner == null || owner.isEmpty()) return;
        
        String senderName = event.getSender().getName();
        
        if (senderName.equalsIgnoreCase(owner)) {
            String message = event.getMessage();
            logger.info("Received private message from owner {}: {}", senderName, message);
            
            // 使用托管的线程池执行任务
            if (XinAgentPlugin.Instance.executorService != null && !XinAgentPlugin.Instance.executorService.isShutdown()) {
                XinAgentPlugin.Instance.executorService.submit(() -> {
                    try {
                        if (XinAgentPlugin.Instance.agentManager != null) {
                            String response = XinAgentPlugin.Instance.agentManager.processMessage(message);
                            logger.info("AI reply to owner: {}", response);
                            
                            String[] lines = response.split("\\r?\\n");
                            for (String line : lines) {
                                if (!line.trim().isEmpty()) {
                                    sendInChunks(senderName, line.trim());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing private message from owner", e);
                    }
                });
            }
        }
    }

    private void sendInChunks(String recipient, String text) {
        int byteLimit = 80;
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            
            if (currentBytes + charBytes > byteLimit) {
                Bot.Instance.sendCommand("tell " + recipient + " " + currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            
            currentChunk.append(c);
            currentBytes += charBytes;
        }
        
        if (currentChunk.length() > 0) {
            Bot.Instance.sendCommand("tell " + recipient + " " + currentChunk.toString());
        }
    }
}
