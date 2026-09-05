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
# 推理强度：none（关闭）、low、medium、high
reasoning_effort=none
# 追加到内置系统提示词末尾的自定义人格/行为提示
soul=你珍惜建筑，不主动破坏玩家作品。
# 禁止在公屏透露的闭区间：minX,minY,minZ,maxX,maxY,maxZ；仅出现X/Z时按水平投影判断；留空禁用
public_chat_forbidden_coordinate_range=-30000000,-2048,-30000000,30000000,2048,30000000
# 发送给模型的记忆窗口条数（<=0 表示不限制，不建议）
max_memory_messages=150
# 自主任务循环的检查间隔（秒，最小 5）
task_loop_interval_seconds=15
# 血量低于该值且仍在下降时主动唤醒 AI 避险（0-20）
low_health_threshold=10.0
# 私聊屏蔽词（逗号分隔，完全匹配时忽略该条私聊）
private_message_blacklist=back,help
```

### OpenAI-compatible 推理流与运行轨迹

`reasoning_effort=none` 使用普通流式模型；设置为 `low`、`medium` 或 `high` 时，XinClaw 使用通用 OpenAI-compatible reasoning SSE 适配器。它会分别处理 `reasoning_content`、可见 `content` 和分片 `tool_calls`，并在工具续调用中按协议回传 reasoning。若网关偶发返回只有 reasoning、没有 content/tool call 的终态，同一请求最多原样重试一次，不会把思维内容伪装成回复。

`AgentManager.subscribeTrace(...)` 可订阅有序的 monotonic runtime trace，包括模型请求、
reasoning/content 流片段、模型响应、工具参数、完整工具返回、Agent 输入/输出及错误。
Benchmark 等外部插件应直接订阅该接口，不应再解析控制台日志。

外部插件可以通过 `AgentToolRegistry` 注册额外工具；XinClaw 会把工具 schema 和结果
交给模型，但核心运行时不依赖任何特定外部检索或感知模型。

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
