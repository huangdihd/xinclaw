package xin.claw.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistentChatMemoryStoreStreamingTest {
    @Test
    void collapsesOnlyConsecutiveDuplicateFinalAssistantMessages() {
        List<ChatMessage> input = List.of(
            UserMessage.from("question"),
            AiMessage.from("final"),
            AiMessage.from("final"),
            AiMessage.from("final"),
            UserMessage.from("next"),
            AiMessage.from("different")
        );

        List<ChatMessage> result = PersistentChatMemoryStore
            .collapseConsecutiveDuplicateFinalAiMessages(input);

        assertEquals(4, result.size());
        assertEquals(UserMessage.from("question"), result.get(0));
        assertEquals(AiMessage.from("final"), result.get(1));
        assertEquals(UserMessage.from("next"), result.get(2));
        assertEquals(AiMessage.from("different"), result.get(3));
    }

    @Test
    void preservesRepeatedFinalTextWhenSeparatedByAnotherMessage() {
        List<ChatMessage> input = List.of(
            AiMessage.from("same"),
            UserMessage.from("separator"),
            AiMessage.from("same")
        );

        assertEquals(
            input,
            PersistentChatMemoryStore.collapseConsecutiveDuplicateFinalAiMessages(input)
        );
    }

    @Test
    void storeNormalizesEveryStreamingMemoryWrite(@TempDir Path directory) {
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(directory.toString());
        store.updateMessages("default", List.of(
            UserMessage.from("question"),
            AiMessage.from("final"),
            AiMessage.from("final"),
            AiMessage.from("final")
        ));

        assertEquals(
            List.of(UserMessage.from("question"), AiMessage.from("final")),
            store.getMessages("default")
        );
    }

    @Test
    void remembersCompletedToolExecutionsAfterMemoryWindowEviction(@TempDir Path directory) {
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(directory.toString());
        assertEquals(0L, store.completedToolExecutionCount());

        store.updateMessages("default", List.of(
            ToolExecutionResultMessage.from("call-1", "whereAmI", "ok")
        ));
        assertEquals(1L, store.completedToolExecutionCount());

        store.updateMessages("default", List.of(AiMessage.from("final after eviction")));
        assertEquals(1L, store.completedToolExecutionCount());

        store.updateMessages("default", List.of(
            ToolExecutionResultMessage.from("call-2", "scanSurroundings", "ok")
        ));
        store.updateMessages("default", List.of(
            ToolExecutionResultMessage.from("call-2", "scanSurroundings", "same result repeated")
        ));
        assertEquals(2L, store.completedToolExecutionCount());
    }

    @Test
    void seedsExistingToolIdsBeforeTheFirstNewTurn(@TempDir Path directory) {
        PersistentChatMemoryStore writer = new PersistentChatMemoryStore(directory.toString());
        writer.updateMessages("default", List.of(
            ToolExecutionResultMessage.from("old-call", "whereAmI", "ok")
        ));

        PersistentChatMemoryStore reopened = new PersistentChatMemoryStore(directory.toString());
        assertEquals(1L, reopened.completedToolExecutionCount());

        reopened.updateMessages("default", List.of(
            ToolExecutionResultMessage.from("old-call", "whereAmI", "ok"),
            AiMessage.from("new tool-less answer")
        ));
        assertEquals(1L, reopened.completedToolExecutionCount());
    }

    @Test
    void toolEventSnapshotsGrowWithTheRetainedMemoryHistory(@TempDir Path directory) {
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(directory.toString());
        int total = 513;
        List<ChatMessage> history = new java.util.ArrayList<>();
        for (int index = 0; index < total; index++) {
            String id = "call-" + index;
            dev.langchain4j.agent.tool.ToolExecutionRequest request = dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                .id(id)
                .name("whereAmI")
                .arguments("{}")
                .build();
            history.add(AiMessage.from(List.of(request)));
            history.add(ToolExecutionResultMessage.from(id, "whereAmI", "ok"));
        }
        store.updateMessages("default", history);

        assertEquals(total, store.completedToolExecutionCount());
        assertEquals(total, store.trackedCompletedToolIdCount());
        assertEquals(total, store.trackedToolCallIdCount());
    }
}
