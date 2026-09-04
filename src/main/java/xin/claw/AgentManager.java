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

import com.google.gson.JsonObject;
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
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import xin.claw.memory.PersistentChatMemoryStore;
import xin.claw.tasks.TaskManager;
import xin.claw.tools.*;
import xin.claw.trace.AgentTraceListener;
import xin.claw.trace.AgentTracePublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentManager {
    private static final Logger logger = LoggerFactory.getLogger(AgentManager.class);
    private static final Pattern ENGLISH_ACTION_REQUEST = Pattern.compile(
        "^(find|locate|enter|navigate|move|go|build|mine|collect|search|scan|check|open|craft|place|dig|attack|follow|walk|travel|show|list|get|stop|use)\\b"
    );
    private static final Pattern ENGLISH_FUTURE_ACTION = Pattern.compile(
        "^(?:i(?:'ll| will)\\s+(?:start|begin)\\b|i(?:'ll| will)\\s+break\\s+(?:this|the task)\\s+into\\b).*"
    );
    private static final Pattern ENGLISH_LET_ME_ACTION = Pattern.compile(
        "^let me\\s+(?:find|locate|enter|navigate|move|go|build|mine|collect|search|scan|check|open|craft|place|dig|attack|follow|walk|travel|show|list|get|stop|use)\\b.*"
    );
    
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
            "2. 移动能力：内置寻路 (pathfindTo) 默认不挖掘：不会破坏任何方块，遇到障碍只绕行、跳跃或搭桥；allowDig=true 才允许挖掘挡路的方块。在机器人的底层引擎跑图时，系统会**自动静默**，绝不会打扰你；只有当它卡住或到达目标时才会发系统事件唤醒你。因此，**如果你刚才下达了移动指令，请直接结束对话，不要画蛇添足地使用 addIdleMovement 强行让自己等待，这会浪费你的算力！**",
            "3. 关门通行：自动寻路不会自动开门，关着的门会被当成碰撞障碍。遇到建筑入口时，先用 pathfindTo（默认不挖掘）到门外相邻可达格；确认到达后，用 findSpecificBlocks 查询小半径内的 door 并读取状态。若 open=false，对门的下半扇坐标调用 interactBlock；再次查询确认 open=true 后，再调用 pathfindTo 进入室内。禁止提前把开门与室内寻路一起入队，不要反复 pathfindTo 尝试穿越关门，也禁止通过挖掘建筑墙体、屋顶或地形来进入建筑（allowDig=true 只允许挖掘天然地形挡路方块，不允许挖建筑本体）。",
            "4. 空间理解：你的坐标通常指你脚底所在的方块，因此与你处于同一高度的方块是y，你眼睛（头部）平齐的方块是y+1，而在你脑袋正上方的方块则是y+2。",
            "5. 空间感知工具语义：scanSurroundings 返回脚下、四向障碍和危险摘要；getAreaMap 与 getAreaMapAt 返回俯视字符地图；getLoadedChunks 返回已加载区块范围；findSpecificBlocks 与 findSpecificBlocksInBounds 返回匹配方块及状态。导航坐标必须来自工具返回的方块、地图或可达点证据。",
            "6. 区域工具使用半开 bounds：min 包含、max_exclusive 不包含，二者均为 [x,y,z] 三整数数组。findSpecificBlocksInBounds、findReachablePointInBounds、previewPathToBounds 和 pathfindToBounds 使用相同 bounds 语义。",
            "7. 路线预览：previewPathTo 预览绝对坐标点路线；previewPathToBounds 预览半开区域内可达点路线。两者只返回选定目标、寻路节点和移动类型，不会设置导航目标或移动机器人。",
            "【工具行动铁律】",
            "- 收到需要在游戏中执行、搜索、移动、建造、交互或管理任务的指令时，必须在当前回复中调用至少一个实际工具；不允许把行动推迟到下一轮。",
            "- 禁止只说『我会开始』『I'll start』『接下来我将……』『Let me...』然后不调用工具。口头承诺不是行动，也不能作为本轮的最终回复。",
            "- 复杂任务应在同一轮直接调用 addTask，并立即使用返回的真实 ID 推进首个任务或调用完成当前步骤所需的感知/搜索/移动工具。",
            "- 纯聊天、解释或报告任务除外；只有这类不需要改变或读取游戏状态的请求可以不调用工具。",
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
    private final AgentTracePublisher tracePublisher;
    private PersistentChatMemoryStore memoryStore;
    public final java.util.concurrent.atomic.AtomicReference<Thread> processingThread = new java.util.concurrent.atomic.AtomicReference<>(null);
    private final ProcessingAdmissionGate processingAdmissionGate = new ProcessingAdmissionGate();
    private final java.util.concurrent.atomic.AtomicBoolean memoryClearRequested = new java.util.concurrent.atomic.AtomicBoolean(false);

    public AgentManager() {
        this.taskManager = new TaskManager();
        this.tracePublisher = new AgentTracePublisher();
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
        StreamingChatLanguageModel model = buildStreamingModel(tracePublisher);

        // Setup persistent chat memory
        String configDir = PluginConfig.getDataDir().getAbsolutePath();
        this.memoryStore = new PersistentChatMemoryStore(configDir, tracePublisher);
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
        return buildStreamingModel(new AgentTracePublisher());
    }

    static StreamingChatLanguageModel buildStreamingModel(AgentTracePublisher tracePublisher) {
        boolean deepSeekModel = PluginConfig.modelName != null
            && PluginConfig.modelName.toLowerCase(java.util.Locale.ROOT).contains("deepseek");
        if (PluginConfig.enableThinking || deepSeekModel) {
            return new SingleTerminalStreamingChatLanguageModel(
                new DeepSeekThinkingStreamingChatLanguageModel(
                    PluginConfig.apiKey,
                    PluginConfig.apiBaseUrl,
                    PluginConfig.modelName,
                    configuredApiTimeout(),
                    PluginConfig.enableThinking,
                    "high",
                    tracePublisher
                )
            );
        }
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(PluginConfig.apiKey)
                .modelName(PluginConfig.modelName)
                .timeout(configuredApiTimeout());

        if (PluginConfig.apiBaseUrl != null && !PluginConfig.apiBaseUrl.trim().isEmpty()) {
            builder.baseUrl(PluginConfig.apiBaseUrl.trim());
        }
        return new SingleTerminalStreamingChatLanguageModel(builder.build());
    }

    public AutoCloseable subscribeTrace(AgentTraceListener listener) {
        return tracePublisher.subscribe(listener);
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

    /**
     * 只请求中断当前 AI 处理，不等待 SSE 流到达终态，也不释放 processingThread 槽位。
     * benchmark 硬截止使用此方法，以便先返回 episode_end；旧流仍由槽位隔离，不能与下一会话并发。
     *
     * @return 发出中断时占用处理槽位的线程；没有在途处理时返回 null
     */
    public Thread requestInterruptProcessing() {
        java.util.concurrent.Future<?> task = currentAgentTask;
        currentAgentTask = null;
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        return requestProcessingThreadInterrupt(processingThread);
    }

    static Thread requestProcessingThreadInterrupt(
        java.util.concurrent.atomic.AtomicReference<Thread> slot
    ) {
        Thread target = slot.get();
        if (target != null) target.interrupt();
        return target;
    }

    public void interruptProcessing() {
        Thread t = requestInterruptProcessing();
        if (t == null) return;
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
        return processMessageIf(message, () -> true);
    }

    public String processMessageIf(String message, BooleanSupplier admissionAllowed) {
        // Ensure the current thread's interrupt flag is cleared before starting, 
        // to prevent thread pool reuse issues causing InterruptedIOException.
        Thread.interrupted();
        
        Thread current = Thread.currentThread();
        java.util.concurrent.atomic.AtomicReference<BotAgent> agentForCall = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<PersistentChatMemoryStore> memoryStoreForCall = new java.util.concurrent.atomic.AtomicReference<>();
        // Lock order is always admission gate -> AgentManager lifecycle lock.
        // Reset/timeout use the same gate, so guard validation and processing-slot
        // acquisition cannot cross cleanup.
        ProcessingAdmissionGate.Result admission = processingAdmissionGate.underGate(() -> {
            synchronized (this) {
                if (this.agent == null || !admissionAllowed.getAsBoolean()) {
                    return ProcessingAdmissionGate.Result.REJECTED;
                }
                if (!processingThread.compareAndSet(null, current)) {
                    return ProcessingAdmissionGate.Result.BUSY;
                }
                agentForCall.set(this.agent);
                memoryStoreForCall.set(this.memoryStore);
                return ProcessingAdmissionGate.Result.ACQUIRED;
            }
        });
        if (admission == ProcessingAdmissionGate.Result.REJECTED) return "";
        if (admission == ProcessingAdmissionGate.Result.BUSY) {
            return "我现在正在思考上一条指令，请稍后再试！";
        }

        try {
            // 此刻确认没有对话在途，安全修复上一轮被打断时可能留下的残缺工具调用序列
            if (memoryStoreForCall.get() != null) {
                memoryStoreForCall.get().repairConversation("default");
            }
            JsonObject inputTrace = new JsonObject();
            inputTrace.addProperty("text", message);
            tracePublisher.emit("agent_input", inputTrace);
            String response = executeWithActionGuard(
                agentForCall.get(),
                message,
                () -> memoryStoreForCall.get() == null ? 0L : memoryStoreForCall.get().completedToolExecutionCount()
            );
            JsonObject outputTrace = new JsonObject();
            outputTrace.addProperty("text", response);
            tracePublisher.emit("agent_output", outputTrace);
            return response;
        } catch (Exception e) {
            boolean isInterrupted = current.isInterrupted() || e.getCause() instanceof InterruptedException || e.toString().toLowerCase().contains("interrupted");
            if (isInterrupted) {
                logger.info("AI processing was interrupted.");
                return ""; // 被打断时返回空字符串，不输出错误
            }
            JsonObject errorTrace = new JsonObject();
            errorTrace.addProperty("error_type", e.getClass().getName());
            errorTrace.addProperty("message", String.valueOf(e.getMessage()));
            tracePublisher.emit("agent_error", errorTrace);
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

    public void blockNewProcessingAdmissions(Runnable cleanup) {
        processingAdmissionGate.blockAdmissions(cleanup);
    }

    static String executeWithActionGuard(
        BotAgent agent,
        String message,
        LongSupplier completedToolResults
    ) {
        long before = snapshotToolResultCount(completedToolResults);
        String first = collectTokenStream(agent.chat(message));
        long after = snapshotToolResultCount(completedToolResults);
        if (!requiresToolAction(message)
            || hasNewToolResult(before, after)
            || !isFutureActionCommitment(first)) {
            return first;
        }
        String correction = "[ACTION_CORRECTION] 你上一条回复只承诺稍后行动，却没有调用任何工具。"
            + "现在必须执行原始指令，并在本回复中调用至少一个合适的实际工具；禁止继续描述将来要做什么。"
            + "\n原始指令：" + message;
        long beforeRetry = after;
        String retry = collectTokenStream(agent.chat(correction));
        long afterRetry = snapshotToolResultCount(completedToolResults);
        if (!hasNewToolResult(beforeRetry, afterRetry)) {
            return "Agent failed to begin execution: no tool was requested.";
        }
        return retry;
    }

    static boolean requiresToolAction(String message) {
        if (message == null) return false;
        String normalized = message.strip().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) return false;
        if (normalized.startsWith("[system_tick]") || normalized.startsWith("[system_event]")) {
            return true;
        }
        String directRequest = normalized.replaceFirst("^please\\s+", "")
            .replaceFirst("^(?:can|could|would|will)\\s+you\\s+", "")
            .replaceFirst("^please\\s+", "");
        if (directRequest.startsWith("explain ")
            || directRequest.startsWith("describe ")
            || directRequest.startsWith("summarize ")
            || directRequest.startsWith("what ")
            || directRequest.startsWith("why ")
            || directRequest.startsWith("how ")
            || directRequest.startsWith("tell me ")) {
            return false;
        }
        if (ENGLISH_ACTION_REQUEST.matcher(directRequest).find()) return true;
        String directChinese = normalized.replaceFirst("^(?:请你|请|你可以|你能|帮我)", "").strip();
        return directChinese.startsWith("寻找")
            || directChinese.startsWith("找到")
            || directChinese.startsWith("找")
            || directChinese.startsWith("进入")
            || directChinese.startsWith("移动")
            || directChinese.startsWith("前往")
            || directChinese.startsWith("建造")
            || directChinese.startsWith("挖掘")
            || directChinese.startsWith("收集")
            || directChinese.startsWith("搜索")
            || directChinese.startsWith("扫描")
            || directChinese.startsWith("检查")
            || directChinese.startsWith("显示")
            || directChinese.startsWith("列出")
            || directChinese.startsWith("获取")
            || directChinese.startsWith("停止")
            || directChinese.startsWith("使用");
    }

    static boolean isFutureActionCommitment(String text) {
        if (text == null) return false;
        String normalized = text.strip().toLowerCase(java.util.Locale.ROOT);
        return ENGLISH_FUTURE_ACTION.matcher(normalized).matches()
            || ENGLISH_LET_ME_ACTION.matcher(normalized).matches()
            || normalized.startsWith("我会开始")
            || normalized.startsWith("我将开始")
            || normalized.startsWith("接下来我");
    }

    static String collectTokenStream(TokenStream stream) {
        if (stream == null) throw new IllegalArgumentException("token stream is required");
        StringBuffer streamedText = new StringBuffer();
        CompletableFuture<String> completed = new CompletableFuture<>();
        stream.onNext(streamedText::append)
            .onComplete(response -> {
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

    private static long snapshotToolResultCount(LongSupplier supplier) {
        return supplier == null ? 0L : supplier.getAsLong();
    }

    private static boolean hasNewToolResult(long before, long after) {
        return after > before;
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
            memoryStore = new PersistentChatMemoryStore(configDir, tracePublisher);
        }
        memoryStore.deleteMessages("default");
        initAgent();
        logger.info("Chat memory cleared and agent re-initialized.");
    }
}
