package ai.forever.gigachat.metadata;

import ai.forever.gigachat.api.chat.completion.CompletionResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.Assert;

public class GigaChatUsage implements Usage {

    public static GigaChatUsage from(CompletionResponse.Usage usage) {
        return new GigaChatUsage(usage);
    }

    private final CompletionResponse.Usage usage;

    protected GigaChatUsage(CompletionResponse.Usage usage) {
        Assert.notNull(usage, "GigaChat Usage must not be null");
        this.usage = usage;
    }

    protected CompletionResponse.Usage getUsage() {
        return this.usage;
    }

    @Override
    public Long getPromptTokens() {
        Integer promptTokens = getUsage().getPromptTokens();
        return promptTokens != null ? promptTokens.longValue() : 0;
    }

    @Override
    public Long getGenerationTokens() {
        Integer generationTokens = getUsage().getCompletionTokens();
        return generationTokens != null ? generationTokens.longValue() : 0;
    }

    @Override
    public Long getTotalTokens() {
        Integer totalTokens = getUsage().getTotalTokens();
        if (totalTokens != null) {
            return totalTokens.longValue();
        } else {
            return getPromptTokens() + getGenerationTokens();
        }
    }

    @Override
    public String toString() {
        return getUsage().toString();
    }
}
