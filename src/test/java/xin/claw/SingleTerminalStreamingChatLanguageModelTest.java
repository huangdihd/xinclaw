package xin.claw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SingleTerminalStreamingChatLanguageModelTest {
    @Test
    void forwardsOnlyOneTerminalCallbackPerGeneration() {
        DoubleTerminalModel delegate = new DoubleTerminalModel();
        SingleTerminalStreamingChatLanguageModel model =
            new SingleTerminalStreamingChatLanguageModel(delegate);
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        model.generate(
            List.of(UserMessage.from("hello")),
            new CountingHandler(completions, errors)
        );

        assertEquals(1, completions.get());
        assertEquals(0, errors.get());
    }

    @Test
    void deduplicatesTerminalCallbacksOnToolAwareGenerationToo() {
        DoubleTerminalModel delegate = new DoubleTerminalModel();
        SingleTerminalStreamingChatLanguageModel model =
            new SingleTerminalStreamingChatLanguageModel(delegate);
        AtomicInteger completions = new AtomicInteger();

        model.generate(
            List.of(UserMessage.from("hello")),
            List.of(ToolSpecification.builder().name("ping").description("ping").build()),
            new CountingHandler(completions, new AtomicInteger())
        );

        assertEquals(1, completions.get());
        assertEquals(1, delegate.toolAwareCalls.get());
    }

    private static final class CountingHandler implements StreamingResponseHandler<AiMessage> {
        private final AtomicInteger completions;
        private final AtomicInteger errors;

        private CountingHandler(AtomicInteger completions, AtomicInteger errors) {
            this.completions = completions;
            this.errors = errors;
        }

        @Override public void onNext(String token) {}
        @Override public void onComplete(Response<AiMessage> response) { completions.incrementAndGet(); }
        @Override public void onError(Throwable error) { errors.incrementAndGet(); }
    }

    private static final class DoubleTerminalModel implements StreamingChatLanguageModel {
        private final AtomicInteger toolAwareCalls = new AtomicInteger();

        @Override
        public void generate(
            List<ChatMessage> messages,
            StreamingResponseHandler<AiMessage> handler
        ) {
            handler.onComplete(Response.from(AiMessage.from("done")));
            handler.onComplete(Response.from(AiMessage.from("done")));
            handler.onError(new IllegalStateException("late duplicate"));
        }

        @Override
        public void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> tools,
            StreamingResponseHandler<AiMessage> handler
        ) {
            toolAwareCalls.incrementAndGet();
            generate(messages, handler);
        }
    }
}
