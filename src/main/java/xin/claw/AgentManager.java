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

package xin.claw;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import xin.claw.memory.PersistentChatMemoryStore;
import xin.claw.tasks.TaskManager;
import xin.claw.tools.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentManager {
    private static final Logger logger = LoggerFactory.getLogger(AgentManager.class);
    
    public interface BotAgent {
        @SystemMessage({
            "你是一个运行在 2b2t（著名的无政府服务器）中的高级智能机器人助理，代号 xinclaw。",
            "【核心能力：任务系统】",
            "1. 你拥有一个持久化的任务列表系统，用于管理长期和短期目标。",
            "2. 当玩家给你一个复杂指令（如：帮我收集木头并盖个房子）时，你应该：",
            "   - 使用 addTask 将其分解为多个具体的子任务。",
            "   - 使用 listTasks 随时查看所有任务的进度。",
            "   - 使用 updateTaskStatus 将任务标记为 IN_PROGRESS（进行中）或 DONE（已完成）。",
            "3. 即使在没有玩家说话时，你也会定期检查任务列表，并自动继续执行标记为 IN_PROGRESS 的任务。",
            "4. 铁律：完成一件任务的『最后一步』永远是调用 updateTaskStatus 把它标记为 DONE。只在回复里说完成了而不改状态 = 任务没有完成，系统会不断重复唤醒你执行它。每次被 [SYSTEM_TICK] 唤醒时，先核对清单里的 IN_PROGRESS 任务是否其实已达成，已达成的立刻标 DONE。",
            "【物理控制与空间感知】",
            "1. 动作队列与异步执行：你的所有物理操作工具（移动、挖掘、放置）都会被加入到『底层引擎的移动队列』中并**异步执行**。你每次思考和决定只负责往队列里『下达指令』，**不需要(也不应该)**在同一次思考循环里反复调用 whereAmI 或 addIdleMovement 来确认结果或挂机等待。",
            "2. 移动能力：内置寻路 (pathfindTo) 支持自动挖掘障碍物和自动搭桥。在机器人的底层引擎跑图时，系统会**自动静默**，绝不会打扰你；只有当它卡住或到达目标时才会发系统事件唤醒你。因此，**如果你刚才下达了移动指令，请直接结束对话，不要画蛇添足地使用 addIdleMovement 强行让自己等待，这会浪费你的算力！**",
            "3. 空间理解：你的坐标通常指你脚底所在的方块，因此与你处于同一高度的方块是y，你眼睛（头部）平齐的方块是y+1，而在你脑袋正上方的方块则是y+2。",
            "4. 感知工具的选择：想快速了解自身处境(脚下/四向/危险)时优先用 scanSurroundings；想直观理解周围布局、规划路线或建筑时用 getAreaMap 获取俯视字符地图(上北下南左西右东)；找特定方块用 findSpecificBlocks，其结果已按距离从近到远排序并附带相对方位。",
            "【行为准则与安全要求】",
            "- 绝对保密：2b2t 中坐标泄露极其危险！绝对不能在公共聊天频道或以任何形式对外透露你的当前坐标（x,y,z）！",
            "- 优先确保生存：随时可以用 getVitals 查询自己的血量和饥饿值。如果血量过低，应优先执行避险任务；当血量骤降时系统也会主动用 [SYSTEM_EVENT] 提醒你。",
            "- 保持进度透明：每当完成一个关键阶段或任务时，主动告知玩家。",
            "- 说话简洁、专业且富有逻辑。"
        })
        String chat(String message);
    }

    private BotAgent agent;
    private TaskManager taskManager;
    private PersistentChatMemoryStore memoryStore;
    public final java.util.concurrent.atomic.AtomicReference<Thread> processingThread = new java.util.concurrent.atomic.AtomicReference<>(null);
    private final java.util.concurrent.atomic.AtomicBoolean memoryClearRequested = new java.util.concurrent.atomic.AtomicBoolean(false);

    public AgentManager() {
        this.taskManager = new TaskManager();
        initAgent();
    }

    public boolean isProcessing() {
        return processingThread.get() != null;
    }

    public void initAgent() {
        var builder = OpenAiChatModel.builder()
                .apiKey(PluginConfig.apiKey)
                .modelName(PluginConfig.modelName);

        if (PluginConfig.apiBaseUrl != null && !PluginConfig.apiBaseUrl.trim().isEmpty()) {
            builder.baseUrl(PluginConfig.apiBaseUrl.trim());
        }

        ChatLanguageModel model = builder.build();

        // Setup persistent chat memory
        String configDir = PluginConfig.getDataDir().getAbsolutePath();
        this.memoryStore = new PersistentChatMemoryStore(configDir);
        this.memoryStore.repairConversation("default");

        int maxMessages = PluginConfig.maxMemoryMessages > 0 ? PluginConfig.maxMemoryMessages : Integer.MAX_VALUE;
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .chatMemoryStore(memoryStore)
                .build();

        this.agent = AiServices.builder(BotAgent.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .tools(
                    new MovementTools(),
                    new PerceptionTools(),
                    new SystemTools(),
                    new SocialTools(),
                    new InventoryTools(),
                    new MemoryTools(),
                    new ActionTools(),
                    new TaskTools(taskManager)
                )
                .build();
    }

    public java.util.concurrent.Future<?> currentAgentTask = null;

    /**
     * 打断当前正在进行的处理（如有），并在插件托管线程池中异步处理消息。
     *
     * @param message     发给 AI 的消息
     * @param onInterrupt 当需要打断旧任务时回调（用于提示用户），可为 null
     * @param onReply     收到非空回复时回调
     */
    public void submitMessage(String message, Runnable onInterrupt, java.util.function.Consumer<String> onReply) {
        if (isProcessing()) {
            if (onInterrupt != null) onInterrupt.run();
            interruptProcessing();
            // 等待一小会儿让旧线程退出
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }

        java.util.concurrent.ExecutorService executor =
                XinClawPlugin.INSTANCE != null ? XinClawPlugin.INSTANCE.executorService : null;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        currentAgentTask = executor.submit(() -> {
            try {
                String response = processMessage(message);
                if (response == null || response.isEmpty()) return;
                onReply.accept(response);
            } catch (Exception e) {
                logger.error("Error while chatting with agent", e);
            }
        });
    }

    public void interruptProcessing() {
        Thread t = processingThread.get();
        if (t != null) {
            t.interrupt();
            // Wait for thread to clear its state or we forcefully set it after a timeout if needed,
            // but usually setting it to null immediately is better so the new task can proceed.
            processingThread.compareAndSet(t, null);
        }
        
        if (currentAgentTask != null && !currentAgentTask.isDone()) {
            currentAgentTask.cancel(true);
            currentAgentTask = null;
        }
    }

    public String processMessage(String message) {
        if (this.agent == null) return "Agent is not initialized.";
        
        // Ensure the current thread's interrupt flag is cleared before starting, 
        // to prevent thread pool reuse issues causing InterruptedIOException.
        Thread.interrupted();
        
        // Prevent overlapping message processing
        Thread current = Thread.currentThread();
        if (!processingThread.compareAndSet(null, current)) {
            return "我现在正在思考上一条指令，请稍后再试！";
        }

        try {
            // 此刻确认没有对话在途，安全修复上一轮被打断时可能留下的残缺工具调用序列
            if (memoryStore != null) {
                memoryStore.repairConversation("default");
            }
            return this.agent.chat(message);
        } catch (Exception e) {
            boolean isInterrupted = current.isInterrupted() || e.getCause() instanceof InterruptedException || e.toString().toLowerCase().contains("interrupted");
            if (isInterrupted) {
                logger.info("AI processing was interrupted.");
                return ""; // 被打断时返回空字符串，不输出错误
            }
            logger.error("Agent error during processing:", e);
            return "Agent error: " + e.getMessage();
        } finally {
            // Clear interrupt flag before returning thread to the pool
            Thread.interrupted();
            processingThread.compareAndSet(current, null);
            // 对话期间收到的清记忆请求延迟到此刻执行，
            // 避免在工具调用循环中途清空记忆导致持久化文件出现残缺的工具调用序列
            if (memoryClearRequested.compareAndSet(true, false)) {
                doClearMemoryNow();
            }
        }
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * 清除对话记忆。
     *
     * @return true 表示已立即清除；false 表示当前正在对话中，已登记为本轮对话结束后自动清除
     */
    public boolean clearMemory() {
        if (this.agent == null) return true;
        if (processingThread.get() != null) {
            memoryClearRequested.set(true);
            return false;
        }
        doClearMemoryNow();
        return true;
    }

    private void doClearMemoryNow() {
        if (memoryStore == null) {
            String configDir = PluginConfig.getDataDir().getAbsolutePath();
            memoryStore = new PersistentChatMemoryStore(configDir);
        }
        memoryStore.deleteMessages("default");
        initAgent();
        logger.info("Chat memory cleared and agent re-initialized.");
    }
}
