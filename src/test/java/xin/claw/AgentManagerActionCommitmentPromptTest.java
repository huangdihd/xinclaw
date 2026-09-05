package xin.claw;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AgentManagerActionCommitmentPromptTest {
    @Test
    void systemPromptRequiresToolsForExecutableTasksInTheSameTurn() throws Exception {
        String prompt = AgentManager.configuredSystemPrompt();

        assertTrue(prompt.contains("必须在当前回复中调用至少一个实际工具"), prompt);
        assertTrue(prompt.contains("禁止只说"), prompt);
        assertTrue(prompt.contains("I'll start"), prompt);
        assertTrue(prompt.contains("不允许把行动推迟到下一轮"), prompt);
        assertTrue(prompt.contains("纯聊天、解释或报告任务除外"), prompt);
    }

    @Test
    void systemPromptExplainsClosedDoorNavigationWorkflow() throws Exception {
        String prompt = AgentManager.configuredSystemPrompt();

        assertTrue(prompt.contains("自动寻路不会自动开门"), prompt);
        assertTrue(prompt.contains("关着的门会被当成碰撞障碍"), prompt);
        assertTrue(prompt.contains("门外相邻可达格"), prompt);
        assertTrue(prompt.contains("open=false"), prompt);
        assertTrue(prompt.contains("interactBlock"), prompt);
        assertTrue(prompt.contains("open=true"), prompt);
        assertTrue(prompt.contains("再调用 pathfindTo 进入室内"), prompt);
        assertTrue(prompt.contains("禁止提前把开门与室内寻路一起入队"), prompt);
    }
}
