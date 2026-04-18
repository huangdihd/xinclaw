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

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import xin.agent.memory.PersistentChatMemoryStore;
import xin.agent.tasks.TaskManager;
import xin.agent.tools.*;

import java.io.File;
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
            "【物理控制与空间感知】",
            "1. 动作队列：你的所有物理操作工具（移动、挖掘、放置）都是异步排队执行的。",
            "2. 移动能力：内置寻路 (pathfindTo) 支持自动挖掘障碍物和自动搭桥。",
            "3. 空间理解：你的坐标通常指你脚底所在的方块，因此与你处于同一高度的方块是y，你眼睛（头部）平齐的方块是y+1，而在你脑袋正上方的方块则是y+2。",
            "【行为准则与安全要求】",
            "- 绝对保密：2b2t 中坐标泄露极其危险！绝对不能在公共聊天频道或以任何形式对外透露你的当前坐标（x,y,z）！",
            "- 优先确保生存：如果血量过低，应优先执行避险任务。",
            "- 保持进度透明：每当完成一个关键阶段或任务时，主动告知玩家。",
            "- 说话简洁、专业且富有逻辑。"
        })
        String chat(String message);
    }

    private BotAgent agent;
    private TaskManager taskManager;
    public final java.util.concurrent.atomic.AtomicReference<Thread> processingThread = new java.util.concurrent.atomic.AtomicReference<>(null);

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
        String pluginDir = xin.bbtt.mcbot.Bot.Instance.getConfig().getConfigData().getPlugin().getDirectory();
        String configDir = pluginDir + File.separator + "XinAgent";
        
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(Integer.MAX_VALUE)
                .chatMemoryStore(new PersistentChatMemoryStore(configDir))
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
        
        // Prevent overlapping message processing
        Thread current = Thread.currentThread();
        if (!processingThread.compareAndSet(null, current)) {
            return "我现在正在思考上一条指令，请稍后再试！";
        }
        
        try {
            return this.agent.chat(message);
        } catch (Exception e) {
            if (current.isInterrupted() || e.getCause() instanceof InterruptedException || e.toString().contains("interrupted")) {
                logger.info("AI processing was interrupted.");
                return ""; // 被打断时返回空字符串，不输出错误
            }
            e.printStackTrace();
            return "Agent error: " + e.getMessage();
        } finally {
            processingThread.compareAndSet(current, null);
        }
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public void clearMemory() {
        if (this.agent == null) return;
        String pluginDir = xin.bbtt.mcbot.Bot.Instance.getConfig().getConfigData().getPlugin().getDirectory();
        String configDir = pluginDir + java.io.File.separator + "XinAgent";
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(configDir);
        store.deleteMessages("default");
        initAgent(); 
    }
}
