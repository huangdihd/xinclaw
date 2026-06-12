# xinclaw - Xinbot 智能 AI 插件

`xinclaw` 是一个基于 **LangChain4j** 框架开发的 **Xinbot** 机器人高级 AI 插件。它集成了大语言模型的 Function Calling 能力，使 Minecraft 机器人能够理解自然语言指令并自主执行复杂的物理操作与环境感知任务。

## 🌟 核心特性

- **深度感知**：支持实时获取机器人坐标、当前服务器、周围方块统计、附近实体详情（自动转换玩家名）以及在线玩家列表。
- **智能行动**：集成 `MovementSync` 插件，支持原版物理特性的平滑走路、看向指定点、跳跃及自动避障。
- **社交交互**：
  - 自动响应配置文件中 `owner` 的私聊指令。
  - 支持游戏内公屏发言与系统指令执行。
  - 具备指令补全建议功能，辅助 AI 准确填参数。
- **物品管理**：实时追踪机器人背包物品，并支持将物品 ID 汉化为可读名称。
- **持久化记忆**：内置基于 JSON 的本地存储，即使机器人重启，AI 依然记得之前的对话上下文。
- **消息优化**：针对 Minecraft 协议进行字节级分段处理，完美绕过 100 字节的聊天长度限制，防止消息乱码。
- **资源友好**：使用托管线程池管理 AI 任务，支持插件卸载时的优雅退出。

## 🛠️ 技术栈

- **核心语言**：Java 17
- **AI 框架**：[LangChain4j](https://github.com/langchain4j/langchain4j) (0.35.0)
- **基础平台**：[Xinbot](https://github.com/huangdihd/xinbot)
- **物理支持**：[MovementSync](https://github.com/huangdihd/movementsync)
- **构建工具**：Maven

## 🚀 安装步骤

1. **编译插件**：
   在项目根目录下执行：
   ```bash
   mvn clean package
   ```
2. **部署**：
   将 `target/XinClaw-1.0-SNAPSHOT.jar` 放入 Xinbot 的 `plugins` 文件夹。
3. **初始化配置**：
   首次启动机器人后，插件会自动在 `plugins/XinClaw/` 目录下生成 `config.properties`（旧版本的 `plugins/XinAgent/` 数据目录会被自动迁移）。
4. **填入 API Key**：
   编辑生成的配置文件，填入你的 OpenAI 或兼容平台（如硅基流动）的 API 信息。

## ⚙️ 配置文件说明

```properties
# API 秘钥
api_key=sk-xxxx...
# API 基础地址（使用 demo 时留空，使用中转地址时填写）
api_base_url=https://api.openai.com/v1
# 模型名称
model_name=gpt-4o-mini
# 开启思考过程（视模型支持情况而定）
enable_thinking=false
# 发送给模型的记忆窗口条数（<=0 表示不限制，不建议）
max_memory_messages=150
# 自主任务循环的检查间隔（秒，最小 5）
task_loop_interval_seconds=15
# 血量低于该值且仍在下降时主动唤醒 AI 避险（0-20）
low_health_threshold=10.0
# 私聊屏蔽词（逗号分隔，完全匹配时忽略该条私聊）
private_message_blacklist=back,help
```

## 🎮 指令使用

- **控制台指令**：
  - `agent <消息>`（别名 `ai`、`bot`）：与 AI 进行对话。
  - `agenttask [list|add <描述>|rm <id>|clear]`（别名 `aitask`）：管理 AI 的任务列表。
  - `agentclear`（别名 `aiclear`）：清除 AI 的所有历史记忆。
  - `agentmodel [<模型名>|list]`（别名 `aimodel`）：切换 AI 模型或列出 API 提供的模型，Tab 补全自动拉取模型列表。
- **游戏内私聊**：
  - 由 Owner 发送私聊给机器人，机器人将自动回复。

## 📄 开源协议

本项目采用 **GPLv3 or later** 协议开源。详情请参阅 [LICENSE](LICENSE) 文件。
