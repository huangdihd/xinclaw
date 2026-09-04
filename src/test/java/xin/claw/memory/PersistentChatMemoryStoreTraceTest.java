package xin.claw.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xin.claw.trace.AgentTraceEvent;
import xin.claw.trace.AgentTracePublisher;

final class PersistentChatMemoryStoreTraceTest {
    @Test
    void emitsEachToolCallAndFullToolResultExactlyOnce(@TempDir Path directory) {
        AgentTracePublisher publisher = new AgentTracePublisher(() -> 99L);
        List<AgentTraceEvent> events = new ArrayList<>();
        publisher.subscribe(events::add);
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(directory.toString(), publisher);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("call-1")
            .name("whereAmI")
            .arguments("{\"detail\":true}")
            .build();
        List<ChatMessage> call = List.of(AiMessage.from(List.of(request)));
        List<ChatMessage> completed = List.of(
            call.get(0),
            ToolExecutionResultMessage.from("call-1", "whereAmI", "x=1.25 y=64 z=-3.5")
        );

        store.updateMessages("default", call);
        store.updateMessages("default", completed);
        store.updateMessages("default", completed);

        assertEquals(List.of("tool_call", "tool_result"), events.stream().map(AgentTraceEvent::eventType).toList());
        assertEquals("whereAmI", events.get(0).payload().get("tool").getAsString());
        assertEquals("{\"detail\":true}", events.get(0).payload().get("arguments").getAsString());
        assertEquals("x=1.25 y=64 z=-3.5", events.get(1).payload().get("result").getAsString());
    }
}
