package xin.agent.trackers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.PublicChatEvent;
import xin.bbtt.mcbot.events.SystemChatMessageEvent;

import java.util.LinkedList;
import java.util.List;

public class ChatTracker implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(ChatTracker.class);
    private static final int MAX_HISTORY = 100;
    
    private final LinkedList<String> chatHistory = new LinkedList<>();

    @EventHandler
    public void onPublicChat(PublicChatEvent event) {
        String sender = "Unknown";
        if (event.getSender() != null) {
            sender = event.getSender().getName();
        }
        String msg = String.format("<%s> %s", sender, event.getMessage());
        addMessage(msg);
    }

    @EventHandler
    public void onSystemChat(SystemChatMessageEvent event) {
        if (event.isOverlay()) return; // 忽略动作条消息
        String text = event.getText();
        if (text != null && !text.trim().isEmpty()) {
            addMessage("[SYSTEM] " + text);
        }
    }

    private synchronized void addMessage(String message) {
        chatHistory.addLast(message);
        if (chatHistory.size() > MAX_HISTORY) {
            chatHistory.removeFirst();
        }
    }

    public synchronized List<String> getRecentMessages(int lines) {
        if (lines <= 0) return new java.util.ArrayList<>();
        int count = Math.min(lines, chatHistory.size());
        return new java.util.ArrayList<>(chatHistory.subList(chatHistory.size() - count, chatHistory.size()));
    }
}
