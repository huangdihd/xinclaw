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

    @Test
    void doesNotReplayMoreThan512RetainedToolEventsWhenAnOrdinaryAiMessageIsAppended(@TempDir Path directory) {
        AgentTracePublisher publisher = new AgentTracePublisher(() -> 99L);
        List<AgentTraceEvent> events = new ArrayList<>();
        publisher.subscribe(events::add);
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(directory.toString(), publisher);
        List<ChatMessage> retained = toolExecutions(513);

        store.updateMessages("default", retained);
        events.clear();

        List<ChatMessage> withOrdinaryAppend = new ArrayList<>(retained);
        withOrdinaryAppend.add(AiMessage.from("ordinary answer"));
        store.updateMessages("default", withOrdinaryAppend);

        assertEquals(513L, store.completedToolExecutionCount());
        assertEquals(List.of(), events);

        List<ChatMessage> withNextCall = new ArrayList<>(withOrdinaryAppend);
        withNextCall.addAll(toolExecutions(1, 513));
        store.updateMessages("default", withNextCall);

        assertEquals(514L, store.completedToolExecutionCount());
        assertEquals(List.of("tool_call", "tool_result"),
            events.stream().map(AgentTraceEvent::eventType).toList());
        assertEquals("call-513", events.get(0).payload().get("id").getAsString());
        assertEquals("call-513", events.get(1).payload().get("id").getAsString());
    }

    @Test
    void reloadAndWindowEvictionDoNotReplayHistoricalToolEvents(@TempDir Path directory) {
        PersistentChatMemoryStore writer = new PersistentChatMemoryStore(directory.toString());
        List<ChatMessage> retained = toolExecutions(513);
        writer.updateMessages("default", retained);

        AgentTracePublisher publisher = new AgentTracePublisher(() -> 99L);
        List<AgentTraceEvent> events = new ArrayList<>();
        publisher.subscribe(events::add);
        PersistentChatMemoryStore reopened = new PersistentChatMemoryStore(directory.toString(), publisher);

        List<ChatMessage> withOrdinaryAppend = new ArrayList<>(retained);
        withOrdinaryAppend.add(AiMessage.from("ordinary answer"));
        reopened.updateMessages("default", withOrdinaryAppend);

        assertEquals(513L, reopened.completedToolExecutionCount());
        assertEquals(List.of(), events);

        List<ChatMessage> afterWindowEviction = new ArrayList<>(retained.subList(retained.size() - 4, retained.size()));
        afterWindowEviction.add(AiMessage.from("window evicted tools"));
        reopened.updateMessages("default", afterWindowEviction);
        assertEquals(513L, reopened.completedToolExecutionCount());
        assertEquals(2, reopened.trackedToolCallIdCount());
        assertEquals(2, reopened.trackedCompletedToolIdCount());
        assertEquals(List.of(), events);

        PersistentChatMemoryStore afterEvictionReload = new PersistentChatMemoryStore(directory.toString(), publisher);
        afterEvictionReload.updateMessages("default", afterWindowEviction);
        assertEquals(2L, afterEvictionReload.completedToolExecutionCount());
        assertEquals(2, afterEvictionReload.trackedToolCallIdCount());
        assertEquals(2, afterEvictionReload.trackedCompletedToolIdCount());
        assertEquals(List.of(), events);
    }

    @Test
    void reloadUsesFallbackKeysForToolEventsWithoutIds(@TempDir Path directory) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("whereAmI")
            .arguments("{\"detail\":true}")
            .build();
        List<ChatMessage> persisted = List.of(
            AiMessage.from(List.of(request)),
            ToolExecutionResultMessage.from(null, "whereAmI", "x=1 y=64 z=-3")
        );
        PersistentChatMemoryStore writer = new PersistentChatMemoryStore(directory.toString());
        writer.updateMessages("default", persisted);

        AgentTracePublisher publisher = new AgentTracePublisher(() -> 99L);
        List<AgentTraceEvent> events = new ArrayList<>();
        publisher.subscribe(events::add);
        PersistentChatMemoryStore reopened = new PersistentChatMemoryStore(directory.toString(), publisher);

        List<ChatMessage> withOrdinaryAppend = new ArrayList<>(persisted);
        withOrdinaryAppend.add(AiMessage.from("ordinary answer"));
        reopened.updateMessages("default", withOrdinaryAppend);

        assertEquals(1L, reopened.completedToolExecutionCount());
        assertEquals(List.of(), events);
    }

    private static List<ChatMessage> toolExecutions(int count) {
        return toolExecutions(count, 0);
    }

    private static List<ChatMessage> toolExecutions(int count, int startingIndex) {
        List<ChatMessage> messages = new ArrayList<>(count * 2);
        for (int index = startingIndex; index < startingIndex + count; index++) {
            String id = "call-" + index;
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(id)
                .name("whereAmI")
                .arguments("{}")
                .build();
            messages.add(AiMessage.from(List.of(request)));
            messages.add(ToolExecutionResultMessage.from(id, "whereAmI", "ok"));
        }
        return messages;
    }
}
