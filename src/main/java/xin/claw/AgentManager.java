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
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
            "   - 使用 addTask 将其分解为多个具体的子任务。addTask 返回真实任务 ID；后续 updateTaskStatus/removeTask 必须原样使用该 ID，禁止猜测或编造 ID。",
            "   - 使用 listTasks 随时查看所有任务的进度。",
            "   - 使用 updateTaskStatus 将任务标记为 IN_PROGRESS（进行中）或 DONE（已完成）。",
            "3. 即使在没有玩家说话时，你也会定期检查任务列表，并自动继续执行标记为 IN_PROGRESS 的任务。",
            "4. 铁律：完成一件任务的『最后一步』永远是调用 updateTaskStatus 把它标记为 DONE。只在回复里说完成了而不改状态 = 任务没有完成，系统会不断重复唤醒你执行它。每次被 [SYSTEM_TICK] 唤醒时，先核对清单里的 IN_PROGRESS 任务是否其实已达成，已达成的立刻标 DONE。",
            "【物理控制与空间感知】",
            "1. 动作队列与异步执行：你的所有物理操作工具（移动、挖掘、放置）都会被加入到『底层引擎的移动队列』中并**异步执行**。你每次思考和决定只负责往队列里『下达指令』，**不需要(也不应该)**在同一次思考循环里反复调用 whereAmI 或 addIdleMovement 来确认结果或挂机等待。",
            "2. 移动能力：内置寻路 (pathfindTo) 支持自动挖掘障碍物和自动搭桥。在机器人的底层引擎跑图时，系统会**自动静默**，绝不会打扰你；只有当它卡住或到达目标时才会发系统事件唤醒你。因此，**如果你刚才下达了移动指令，请直接结束对话，不要画蛇添足地使用 addIdleMovement 强行让自己等待，这会浪费你的算力！**",
            "3. 空间理解：你的坐标通常指你脚底所在的方块，因此与你处于同一高度的方块是y，你眼睛（头部）平齐的方块是y+1，而在你脑袋正上方的方块则是y+2。",
            "4. 感知工具的选择：想快速了解自身处境(脚下/四向/危险)时优先用 scanSurroundings；想直观理解周围布局、规划路线或建筑时用 getAreaMap 获取俯视字符地图(上北下南左西右东)；找特定方块用 findSpecificBlocks，其结果已按距离从近到远排序并附带相对方位。",
            "5. 区域作用域与行动验证：当 searchVoxelRegion 返回语义候选 bounds 后，旧的当前位置工具仍可用于普通任务，但不要丢弃候选区域。调用 findSpecificBlocksInBounds、findReachablePointInBounds、previewPathToBounds 或 pathfindToBounds 时，必须把 bounds.min 和 bounds.max_exclusive 两个 [x,y,z] 数组原样复制到同名参数，禁止拆成六个数或重排坐标。",
            "6. 路线预览：previewPathTo 是绝对坐标点的只读路径预览，决定执行后再调用 pathfindTo；previewPathToBounds 是候选半开区域的只读路径预览，决定执行后再调用 pathfindToBounds。两者都只计算并返回选定目标、寻路节点和移动类型，不会设置导航目标或移动机器人。需要先理解从哪一侧接近、最后几步落在哪里时可以调用。",
            "7. 对于『寻找并前往目标』的导航任务，CLMCP Rank 1 是需要通过行动验证的最佳语义假设：默认立即调用 pathfindToBounds 前往 Rank 1。不得在远处用 getAreaMapAt 重新判断结构类别，因为字符方块切片不具备完整三维语义。到达候选后，才使用 getAreaMapAt、findSpecificBlocksInBounds 等局部工具寻找入口、楼梯和内部；只有在候选被局部证伪后才尝试下一 rank。",
            "【行为准则与安全要求】",
            "- 绝对保密：2b2t 中坐标泄露极其危险！绝对不能在公共聊天频道或以任何形式对外透露你的当前坐标（x,y,z）！",
            "- 优先确保生存：随时可以用 getVitals 查询自己的血量和饥饿值。如果血量过低，应优先执行避险任务；当血量骤降时系统也会主动用 [SYSTEM_EVENT] 提醒你。",
            "- 保持进度透明：每当完成一个关键阶段或任务时，主动告知玩家。",
            "- 说话简洁、专业且富有逻辑。"
        })
        TokenStream chat(String message);
    }

    private BotAgent agent;
    private TaskManager taskManager;
    private final AgentToolRegistry toolRegistry;
    private PersistentChatMemoryStore memoryStore;
    public final java.util.concurrent.atomic.AtomicReference<Thread> processingThread = new java.util.concurrent.atomic.AtomicReference<>(null);
    private final java.util.concurrent.atomic.AtomicBoolean memoryClearRequested = new java.util.concurrent.atomic.AtomicBoolean(false);

    public AgentManager() {
        this.taskManager = new TaskManager();
        this.toolRegistry = new AgentToolRegistry(java.util.List.of(
            new MovementTools(),
            new PerceptionTools(),
            new SystemTools(),
            new SocialTools(),
            new InventoryTools(),
            new MemoryTools(),
            new ActionTools(),
            new TaskTools(taskManager)
        ));
        initAgent();
    }

    public boolean isProcessing() {
        return processingThread.get() != null;
    }

    public synchronized void initAgent() {
        StreamingChatLanguageModel model = buildStreamingModel();

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
                .streamingChatLanguageModel(model)
                .chatMemory(chatMemory)
                .tools(toolRegistry.snapshot().toArray())
                .build();
    }

    static StreamingChatLanguageModel buildStreamingModel() {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(PluginConfig.apiKey)
                .modelName(PluginConfig.modelName)
                .timeout(configuredApiTimeout());

        if (PluginConfig.apiBaseUrl != null && !PluginConfig.apiBaseUrl.trim().isEmpty()) {
            builder.baseUrl(PluginConfig.apiBaseUrl.trim());
        }
        return new SingleTerminalStreamingChatLanguageModel(builder.build());
    }

    static Duration configuredApiTimeout() {
        return Duration.ofSeconds(Math.max(10, PluginConfig.apiTimeoutSeconds));
    }

    /** Register a tool supplied by another XinBot plugin and rebuild the AI service. */
    public synchronized void registerExternalTool(Object tool) {
        if (isProcessing()) {
            throw new IllegalStateException("cannot register tools while the agent is processing");
        }
        toolRegistry.registerExternal(tool, this::initAgent);
    }

    /** Remove a previously registered external tool by object identity. */
    public synchronized void unregisterExternalTool(Object tool) {
        if (isProcessing()) {
            throw new IllegalStateException("cannot unregister tools while the agent is processing");
        }
        toolRegistry.unregisterExternal(tool, this::initAgent);
    }

    private volatile java.util.concurrent.Future<?> currentAgentTask = null;
    private final Object submitLock = new Object();
    private final java.util.concurrent.atomic.AtomicLong submissionSeq = new java.util.concurrent.atomic.AtomicLong();

    /**
     * 打断当前正在进行的处理（如有），并在插件托管线程池中异步处理消息。
     *
     * @param message     发给 AI 的消息
     * @param onInterrupt 当需要打断旧任务时回调（用于提示用户），可为 null
     * @param onReply     收到非空回复时回调
     */
    public void submitMessage(String message, Runnable onInterrupt, java.util.function.Consumer<String> onReply) {
        java.util.concurrent.ExecutorService executor =
                XinClawPlugin.INSTANCE != null ? XinClawPlugin.INSTANCE.executorService : null;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        // 整个"打断旧会话→提交新会话"的流程放到线程池里并用锁串行化：
        // 既不阻塞事件线程（打断等待最长 5 秒），也避免多个触发源同时提交导致并发会话
        executor.submit(() -> {
            synchronized (submitLock) {
                if (isProcessing() && onInterrupt != null) onInterrupt.run();
                interruptProcessing();

                if (executor.isShutdown()) return;
                final long seq = submissionSeq.incrementAndGet();
                currentAgentTask = executor.submit(() -> {
                    // 启动前确认自己仍是最新一条消息，被更新的提交取代时直接放弃
                    if (submissionSeq.get() != seq) return;
                    try {
                        String response = processMessage(message);
                        if (response == null || response.isEmpty()) return;
                        onReply.accept(response);
                    } catch (Exception e) {
                        logger.error("Error while chatting with agent", e);
                    }
                });
            }
        });
    }

    public void interruptProcessing() {
        // 先取消尚未开始执行的排队任务，防止等待旧线程期间它又启动新会话
        java.util.concurrent.Future<?> task = currentAgentTask;
        currentAgentTask = null;
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }

        Thread t = processingThread.get();
        if (t == null) return;
        t.interrupt();
        waitForProcessingThreadToFinish(
            processingThread,
            t,
            5000,
            () -> logger.warn("AI 处理线程在 5 秒内未响应中断；为防止流式回调与新会话并发，继续等待旧流到达终态。")
        );
    }

    static void waitForProcessingThreadToFinish(
        java.util.concurrent.atomic.AtomicReference<Thread> slot,
        Thread target,
        long warnAfterMillis,
        Runnable onSlowWait
    ) {
        boolean callerInterrupted = false;
        boolean warned = false;
        long warningDeadline = System.currentTimeMillis() + Math.max(0, warnAfterMillis);
        while (slot.get() == target) {
            if (!warned && System.currentTimeMillis() >= warningDeadline) {
                warned = true;
                if (onSlowWait != null) onSlowWait.run();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException error) {
                callerInterrupted = true;
            }
        }
        if (callerInterrupted) Thread.currentThread().interrupt();
    }

    public String processMessage(String message) {
        // Ensure the current thread's interrupt flag is cleared before starting, 
        // to prevent thread pool reuse issues causing InterruptedIOException.
        Thread.interrupted();
        
        Thread current = Thread.currentThread();
        BotAgent agentForCall;
        PersistentChatMemoryStore memoryStoreForCall;
        // Share the same lifecycle lock as tool registration/rebuild. Once this
        // slot is acquired, registerExternalTool sees isProcessing()==true.
        synchronized (this) {
            if (this.agent == null) return "Agent is not initialized.";
            if (!processingThread.compareAndSet(null, current)) {
                return "我现在正在思考上一条指令，请稍后再试！";
            }
            agentForCall = this.agent;
            memoryStoreForCall = this.memoryStore;
        }

        try {
            // 此刻确认没有对话在途，安全修复上一轮被打断时可能留下的残缺工具调用序列
            if (memoryStoreForCall != null) {
                memoryStoreForCall.repairConversation("default");
            }
            return collectTokenStream(agentForCall.chat(message));
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

    static String collectTokenStream(TokenStream stream) {
        if (stream == null) throw new IllegalArgumentException("token stream is required");
        StringBuffer streamedText = new StringBuffer();
        CompletableFuture<String> completed = new CompletableFuture<>();
        stream.onNext(streamedText::append)
            .onComplete(response -> {
                if (response != null && response.content() != null
                    && response.content().hasToolExecutionRequests()) {
                    return;
                }
                String finalText = response != null && response.content() != null
                    ? response.content().text()
                    : null;
                completed.complete(finalText != null ? finalText : streamedText.toString());
            })
            .onError(completed::completeExceptionally);
        stream.start();

        boolean interrupted = false;
        String result = null;
        Throwable failure = null;
        while (result == null && failure == null) {
            try {
                result = completed.get();
            } catch (InterruptedException error) {
                interrupted = true;
            } catch (ExecutionException error) {
                failure = error.getCause();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                "streaming chat interrupted after terminal callback",
                failure != null ? failure : new InterruptedException("streaming chat interrupted")
            );
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure != null) throw new RuntimeException("streaming chat failed", failure);
        return result;
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
