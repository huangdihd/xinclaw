package xin.claw.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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
}
