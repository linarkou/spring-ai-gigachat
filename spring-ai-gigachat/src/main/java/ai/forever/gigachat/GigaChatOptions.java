package ai.forever.gigachat;

import ai.forever.gigachat.api.chat.GigaChatApi;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.Assert;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GigaChatOptions implements FunctionCallingOptions, ChatOptions {

    @JsonProperty("model")
    private GigaChatApi.ChatModel model;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("repetition_penalty")
    private Double repetitionPenalty;

    @JsonProperty("update_interval")
    private Double updateInterval;

    @JsonIgnore
    private Map<String, Object> toolContext;

    @JsonIgnore
    private List<FunctionCallback> functionCallbacks = new ArrayList<>();

    @JsonIgnore
    private Set<String> functions = new HashSet<>();

    private FunctionCallMode functionCallMode;

    @Override
    public String getModel() {
        return model.getName();
    }

    @Override
    @JsonIgnore
    public Double getFrequencyPenalty() {
        throw new UnsupportedOperationException("Unimplemented method 'getFrequencyPenalty'");
    }

    @Override
    @JsonIgnore
    public Double getPresencePenalty() {
        throw new UnsupportedOperationException("Unimplemented method 'getPresencePenalty'");
    }

    @Override
    @JsonIgnore
    public List<String> getStopSequences() {
        throw new UnsupportedOperationException("Unimplemented method 'getStopSequences'");
    }

    @Override
    public Double getTemperature() {
        return temperature;
    }

    @Override
    public Double getTopP() {
        return topP;
    }

    @Override
    public ChatOptions copy() {
        return this.toBuilder().build();
    }

    @Override
    @JsonIgnore
    public Integer getTopK() {
        throw new UnsupportedOperationException("Unimplemented method 'getTopK'");
    }

    @Override
    public List<FunctionCallback> getFunctionCallbacks() {
        return this.functionCallbacks;
    }

    @Override
    public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
        Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
        this.functionCallbacks = functionCallbacks;
    }

    @Override
    public Set<String> getFunctions() {
        return this.functions;
    }

    @Override
    public void setFunctions(Set<String> functions) {
        Assert.notNull(functions, "Function must not be null");
        this.functions = functions;
    }

    @Override
    public Map<String, Object> getToolContext() {
        return this.toolContext;
    }

    @Override
    public void setToolContext(Map<String, Object> toolContext) {
        this.toolContext = toolContext;
    }

    public FunctionCallMode getFunctionCallMode() {
        return functionCallMode;
    }

    public void setFunctionCallMode(FunctionCallMode functionCallMode) {
        this.functionCallMode = functionCallMode;
    }

    public enum FunctionCallMode {
        /** GigaChat не будет вызывать функции. */
        NONE,
        /** В зависимости от содержимого запроса, модель решает сгенерировать сообщение или вызвать функцию. */
        AUTO
    }
}
