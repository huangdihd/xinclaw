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

package xin.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.agent.XinAgentPlugin;

public class MemoryTools {
    private static final Logger logger = LoggerFactory.getLogger(MemoryTools.class);

    @Tool("清除机器人的所有对话记忆（历史记录）。当你发现机器人胡言乱语或者需要开始新话题时使用。")
    public String clearMemory() {
        logger.info("[AI Tool Call] 调用了 clearMemory()");
        if (XinAgentPlugin.Instance == null || XinAgentPlugin.Instance.agentManager == null) {
            return "清除记忆失败，插件未就绪。";
        }
        
        XinAgentPlugin.Instance.agentManager.clearMemory();
        return "记忆已清除，我们可以重新开始了。";
    }
}
