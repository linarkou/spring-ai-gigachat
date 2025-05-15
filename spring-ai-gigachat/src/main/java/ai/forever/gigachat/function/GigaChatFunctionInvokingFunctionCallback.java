package ai.forever.gigachat.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.Assert;

public class GigaChatFunctionInvokingFunctionCallback<I, O> extends AbstractFunctionCallback<I, O>
        implements GigaChatFunctionCallback {
    private final BiFunction<I, ToolContext, O> biFunction;
    // ниже - дополнительные для GigaChat поля (необязательные)
    private final List<FewShotExample> fewShotExamples;
    private final String outputTypeSchema;

    public GigaChatFunctionInvokingFunctionCallback(
            String name,
            String description,
            String inputTypeSchema,
            Type inputType,
            Function<O, String> responseConverter,
            ObjectMapper objectMapper,
            BiFunction<I, ToolContext, O> function,
            List<FewShotExample> fewShotExamples,
            String outputTypeSchema) {
        super(name, description, inputTypeSchema, inputType, responseConverter, objectMapper);

        Assert.notNull(function, "Function must not be null");
        this.biFunction = function;

        this.fewShotExamples = fewShotExamples == null ? Collections.emptyList() : fewShotExamples;
        this.outputTypeSchema = outputTypeSchema;
    }

    public String getOutputTypeSchema() {
        return this.outputTypeSchema;
    }

    public List<FewShotExample> getFewShotExamples() {
        return this.fewShotExamples;
    }

    @Override
    public O apply(I input, ToolContext context) {
        return this.biFunction.apply(input, context);
    }
}
