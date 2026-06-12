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

package xin.claw.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.claw.XinClawPlugin;

public class MemoryTools {
    private static final Logger logger = LoggerFactory.getLogger(MemoryTools.class);

    @Tool("清除机器人的所有对话记忆（历史记录）。当你发现机器人胡言乱语或者需要开始新话题时使用。")
    public String clearMemory() {
        logger.info("[AI Tool Call] 调用了 clearMemory()");
        if (XinClawPlugin.INSTANCE == null || XinClawPlugin.INSTANCE.agentManager == null) {
            return "清除记忆失败，插件未就绪。";
        }
        
        int taskCount = XinClawPlugin.INSTANCE.agentManager.getTaskManager() != null
                ? XinClawPlugin.INSTANCE.agentManager.getTaskManager().getTasks().size() : 0;
        String taskReminder = taskCount > 0
                ? "注意：任务列表不会随记忆一起清除，当前还有 " + taskCount + " 个任务。清除记忆后你将不记得它们的来龙去脉，如果玩家想要完全重置，请同时调用 clearAllTasks 清空任务。"
                : "";

        boolean immediate = XinClawPlugin.INSTANCE.agentManager.clearMemory();
        if (immediate) {
            return "记忆已清除，我们可以重新开始了。" + taskReminder;
        }
        return "已登记清除请求：为保证数据完整，记忆将在你本轮回复结束后自动清空。" + taskReminder
                + "处理完后请立即结束本轮对话，不要再调用其他工具。";
    }
}
