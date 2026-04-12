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

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import xin.agent.memory.PersistentChatMemoryStore;
import xin.agent.tools.*;

import java.io.File;

public class AgentManager {
    
    public interface BotAgent {
        @SystemMessage({
            "你是一个运行在 Minecraft 服务器中的智能机器人助理，你的代号是 xinclaw。",
            "【技术背景】",
            "1. 核心框架：基于 LangChain4j，支持多工具连续调用 (Function Calling)。",
            "2. 基础平台：运行在 Xinbot 框架上，通过 MovementSync 插件控制物理行为。",
            "3. 动作机制：你的移动和物理操作是【队列式】执行的。当你连续调用多个移动或动作工具时，它们会按顺序排队执行。",
            "【操作指南】",
            "- 你可以一次性决定多个动作。例如：调用 pathfindTo 前往某处 -> 调用 addIdleMovement 等待 1 秒 -> 调用 jump 跳跃。",
            "- 动作耗时参考：",
            "  * 走路/寻路：约每秒 4 格。",
            "  * 转头：瞬间完成 (~100ms)。",
            "  * 跳跃：约 500ms。",
            "  * 吃东西/喝药水：约 1600ms (使用 useItemWithDuration)。",
            "  * 拉满弓：约 1000ms (使用 useItemWithDuration)。",
            "【行为准则】",
            "- 调用工具获取位置(whereAmI)、观察环境(getBlocksInCube/getNearbyPlayers)和管理背包(getInventory)。",
            "- 灵活使用 addIdleMovement 在动作之间插入延迟。",
            "- 请以简洁、专业且富有亲和力的口吻与玩家沟通。"
        })
        String chat(String message);
    }

    private BotAgent agent;

    public AgentManager() {
        initAgent();
    }

    private void initAgent() {
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
        
        var chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
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
                    new ActionTools()
                )
                .build();
    }

    public String processMessage(String message) {
        if (this.agent == null) return "Agent is not initialized.";
        try {
            return this.agent.chat(message);
        } catch (Exception e) {
            e.printStackTrace();
            return "Agent error: " + e.getMessage();
        }
    }

    public void clearMemory() {
        if (this.agent == null) {
            return;
        }
        
        String pluginDir = xin.bbtt.mcbot.Bot.Instance.getConfig().getConfigData().getPlugin().getDirectory();
        String configDir = pluginDir + java.io.File.separator + "XinAgent";
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(configDir);
        
        // In LangChain4j, memory is stored per ChatMemory instance which defaults to the memoryId "default"
        store.deleteMessages("default");
        
        // Re-initialize to ensure the proxy grabs the freshly cleared memory
        initAgent(); 
    }
}
