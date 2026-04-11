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

import java.io.File;

public class AgentManager {
    
    public interface BotAgent {
        @SystemMessage({
            "你是一个运行在 Minecraft 服务器中的智能机器人助理，你的代号是 xinclaw。",
            "【技术背景】",
            "1. 核心框架：你基于 LangChain4j 框架开发，集成了大语言模型的 Function Calling 能力。",
            "2. 基础平台：你运行在 Xinbot 机器人框架之上，这是一个基于 Java 编写的 Minecraft 协议机器人。",
            "3. 物理引擎：你通过接入 MovementSync 插件获得了物理模拟和移动控制能力，支持原版物理特性。",
            "4. 交互能力：你可以通过指令与服务器交互，通过反射获取世界实体和方块信息，并具备物品栏追踪功能。",
            "【行为准则】",
            "- 你可以调用工具获取位置(whereAmI)、观察周围方块(getBlocksInCube)、查看实体(getNearbyEntities)和管理背包(getInventory)。",
            "- 你可以自主决定移动(walkTo)、看向(lookAt)或跳跃(jump)来完成任务。",
            "- 你可以发送聊天消息(sendChatMessage)或执行系统指令(sendCommand)。",
            "- 请以简洁、专业且富有亲和力的口吻与玩家沟通。作为 Xinbot 的高级 AI 插件，你应展示出对 Minecraft 机制和自身技术栈的了解。"
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
                    new xin.agent.tools.MovementTools(),
                    new xin.agent.tools.PerceptionTools(),
                    new xin.agent.tools.SystemTools(),
                    new xin.agent.tools.SocialTools(),
                    new xin.agent.tools.InventoryTools(),
                    new xin.agent.tools.MemoryTools()
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
        if (this.agent != null) {
            String pluginDir = xin.bbtt.mcbot.Bot.Instance.getConfig().getConfigData().getPlugin().getDirectory();
            String configDir = pluginDir + java.io.File.separator + "XinAgent";
            PersistentChatMemoryStore store = new PersistentChatMemoryStore(configDir);
            
            // In LangChain4j, memory is stored per ChatMemory instance which defaults to the memoryId "default"
            store.deleteMessages("default");
            
            // Re-initialize to ensure the proxy grabs the freshly cleared memory
            initAgent(); 
        }
    }
}
