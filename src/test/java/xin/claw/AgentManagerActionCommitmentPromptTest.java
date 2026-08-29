package xin.claw;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;

final class AgentManagerActionCommitmentPromptTest {
    @Test
    void systemPromptRequiresToolsForExecutableTasksInTheSameTurn() throws Exception {
        SystemMessage system = AgentManager.BotAgent.class
            .getMethod("chat", String.class)
            .getAnnotation(SystemMessage.class);
        String prompt = String.join("\n", system.value());

        assertTrue(prompt.contains("必须在当前回复中调用至少一个实际工具"), prompt);
        assertTrue(prompt.contains("禁止只说"), prompt);
        assertTrue(prompt.contains("I'll start"), prompt);
        assertTrue(prompt.contains("不允许把行动推迟到下一轮"), prompt);
        assertTrue(prompt.contains("纯聊天、解释或报告任务除外"), prompt);
    }
}
