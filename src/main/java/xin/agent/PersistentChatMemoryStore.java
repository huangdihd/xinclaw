package xin.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PersistentChatMemoryStore implements ChatMemoryStore {
    private static final Logger logger = LoggerFactory.getLogger(PersistentChatMemoryStore.class);
    private final Path filePath;

    public PersistentChatMemoryStore(String pluginDir) {
        this.filePath = new File(pluginDir + File.separator + "chat-memory.json").toPath();
        ensureFileExists();
    }

    private void ensureFileExists() {
        try {
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, "[]".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            logger.error("Failed to create memory store file", e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String json = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (IOException e) {
            logger.error("Failed to read messages from store", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            Files.write(filePath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to update messages in store", e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            Files.write(filePath, "[]".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to clear messages in store", e);
        }
    }
}
