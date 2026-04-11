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
