package xin.claw;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Guards LangChain4j/OpenAI streaming generations against duplicate terminal callbacks. */
final class SingleTerminalStreamingChatLanguageModel implements StreamingChatLanguageModel {
    private final StreamingChatLanguageModel delegate;

    SingleTerminalStreamingChatLanguageModel(StreamingChatLanguageModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void generate(
        List<ChatMessage> messages,
        StreamingResponseHandler<AiMessage> handler
    ) {
        delegate.generate(messages, singleTerminal(handler));
    }

    @Override
    public void generate(
        List<ChatMessage> messages,
        List<ToolSpecification> tools,
        StreamingResponseHandler<AiMessage> handler
    ) {
        delegate.generate(messages, tools, singleTerminal(handler));
    }

    @Override
    public void generate(
        List<ChatMessage> messages,
        ToolSpecification tool,
        StreamingResponseHandler<AiMessage> handler
    ) {
        delegate.generate(messages, tool, singleTerminal(handler));
    }

    private static StreamingResponseHandler<AiMessage> singleTerminal(
        StreamingResponseHandler<AiMessage> handler
    ) {
        Objects.requireNonNull(handler, "handler");
        AtomicBoolean terminal = new AtomicBoolean(false);
        return new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                if (!terminal.get()) handler.onNext(token);
            }

            @Override
            public void onComplete(dev.langchain4j.model.output.Response<AiMessage> response) {
                if (terminal.compareAndSet(false, true)) handler.onComplete(response);
            }

            @Override
            public void onError(Throwable error) {
                if (terminal.compareAndSet(false, true)) handler.onError(error);
            }
        };
    }
}
