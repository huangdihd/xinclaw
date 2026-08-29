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

package xin.claw.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
    private final java.util.Set<String> completedToolExecutionIds =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong completedToolExecutionCount =
        new java.util.concurrent.atomic.AtomicLong();

    public PersistentChatMemoryStore(String pluginDir) {
        this.filePath = new File(pluginDir + File.separator + "chat-memory.json").toPath();
        ensureFileExists();
        recordCompletedToolExecutions(getMessages("default"));
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

    /**
     * 在新一轮对话开始前修复记忆中残缺的工具调用序列。
     *
     * 注意：禁止在 getMessages 中做这件事——工具调用循环进行中，
     * "assistant 已带工具调用、结果尚未写入"是合法的中间态，
     * 读取时修复会把正常对话拆得七零八落。只有在确认没有对话在途时，
     * 尾部的未完成工具调用组才是真正的损坏（由打断或崩溃造成）。
     */
    public void repairConversation(Object memoryId) {
        List<ChatMessage> messages = getMessages(memoryId);
        List<ChatMessage> repaired = repairToolCallSequences(messages);
        if (repaired.size() != messages.size()) {
            logger.warn("检测到记忆文件中存在残缺的工具调用序列(通常由对话被打断导致)，已自动修复并移除 {} 条消息。",
                    messages.size() - repaired.size());
            updateMessages(memoryId, repaired);
        }
    }

    /**
     * 修复消息序列中残缺的工具调用：OpenAI 协议要求 tool 消息必须紧跟在
     * 发起对应工具调用的 assistant 消息之后，且每个工具调用都必须有结果。
     * 对话被打断或记忆被中途清除都可能破坏这一约束，导致后续请求被 API 拒绝。
     */
    private static List<ChatMessage> repairToolCallSequences(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>(messages.size());
        java.util.Set<String> pendingToolIds = new java.util.HashSet<>();

        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage toolResult) {
                String key = toolResult.id() != null ? toolResult.id() : toolResult.toolName();
                if (!pendingToolIds.remove(key)) {
                    continue; // 孤儿工具结果(没有对应的工具调用)，丢弃
                }
                result.add(msg);
                continue;
            }

            if (!pendingToolIds.isEmpty()) {
                // 上一组工具调用尚未集齐结果就出现了其他消息：移除整组残缺调用
                removeTrailingIncompleteGroup(result);
                pendingToolIds.clear();
            }

            if (msg instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
                    pendingToolIds.add(request.id() != null ? request.id() : request.name());
                }
            }
            result.add(msg);
        }

        if (!pendingToolIds.isEmpty()) {
            removeTrailingIncompleteGroup(result);
        }
        return result;
    }

    /** 从尾部移除连续的工具结果及其所属的带工具调用的助手消息。 */
    private static void removeTrailingIncompleteGroup(List<ChatMessage> result) {
        while (!result.isEmpty() && result.get(result.size() - 1) instanceof ToolExecutionResultMessage) {
            result.remove(result.size() - 1);
        }
        if (!result.isEmpty()
                && result.get(result.size() - 1) instanceof AiMessage ai
                && ai.hasToolExecutionRequests()) {
            result.remove(result.size() - 1);
        }
    }

    static List<ChatMessage> collapseConsecutiveDuplicateFinalAiMessages(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>(messages.size());
        AiMessage previousFinal = null;
        for (ChatMessage message : messages) {
            if (message instanceof AiMessage ai && !ai.hasToolExecutionRequests()) {
                if (previousFinal != null && java.util.Objects.equals(previousFinal.text(), ai.text())) {
                    continue;
                }
                previousFinal = ai;
            } else {
                previousFinal = null;
            }
            result.add(message);
        }
        return result;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            List<ChatMessage> normalized = collapseConsecutiveDuplicateFinalAiMessages(messages);
            recordCompletedToolExecutions(normalized);
            String json = ChatMessageSerializer.messagesToJson(normalized);
            Files.write(filePath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to update messages in store", e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            Files.write(filePath, "[]".getBytes(StandardCharsets.UTF_8));
            completedToolExecutionIds.clear();
            completedToolExecutionCount.set(0L);
        } catch (IOException e) {
            logger.error("Failed to clear messages in store", e);
        }
    }

    public long completedToolExecutionCount() {
        return completedToolExecutionCount.get();
    }

    private void recordCompletedToolExecutions(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage result) {
                String id = result.id();
                if (id != null && !id.isBlank() && completedToolExecutionIds.add(id)) {
                    completedToolExecutionCount.incrementAndGet();
                }
            }
        }
    }
}
