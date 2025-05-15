package ai.forever.gigachat.function;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.core.ParameterizedTypeReference;

public interface GigaChatFunctionCallback extends FunctionCallback {
    static GigaChatFunctionCallbackBuilder builder() {
        return new GigaChatFunctionCallbackBuilder();
    }

    /**
     * @return Returns the JSON schema of the function output type.
     */
    String getOutputTypeSchema();

    /**
     * @return Returns the list of user request and function input params examples.
     */
    List<FewShotExample> getFewShotExamples();

    /**
     * @param request User request.
     * @param params Function input example corresponding to user request.
     */
    record FewShotExample(String request, Object params) {}

    /**
     * Копия {@link FunctionCallback.Builder} с изменением под GigaChat.
     *
     * Builder for creating a {@link FunctionCallback} instance. This is a hierarchical
     * builder with the following structure:
     * <ul>
     * <li>{@link GigaChatFunctionCallback.Builder} - The root builder interface.
     * <li>{@link GigaChatFunctionInvokingSpec} - The function invoking builder interface.
     * <li>{@link GigaChatMethodInvokingSpec} - The method invoking builder interface.
     * </ul>
     */
    interface Builder {

        /**
         * Builds a {@link Function} invoking {@link FunctionCallback} instance.
         */
        <I, O> GigaChatFunctionInvokingSpec<I, O> function(String name, Function<I, O> function);

        /**
         * Builds a {@link BiFunction} invoking {@link FunctionCallback} instance.
         */
        <I, O> GigaChatFunctionInvokingSpec<I, O> function(String name, BiFunction<I, ToolContext, O> biFunction);

        /**
         * Builds a {@link Supplier} invoking {@link FunctionCallback} instance.
         */
        <O> GigaChatFunctionInvokingSpec<Void, O> function(String name, Supplier<O> supplier);

        /**
         * Builds a {@link Consumer} invoking {@link FunctionCallback} instance.
         */
        <I> GigaChatFunctionInvokingSpec<I, Void> function(String name, Consumer<I> consumer);

        /**
         * Builds a Method invoking {@link FunctionCallback} instance.
         */
        GigaChatMethodInvokingSpec method(String methodName, Class<?>... argumentTypes);
    }

    interface GigaChatCommonCallbackInvokingSpec<B extends GigaChatCommonCallbackInvokingSpec<B>>
            extends FunctionCallback.CommonCallbackInvokingSpec<B> {
        /**
         * Adds single example to the list of few shot examples.
         * @param fewShotExample Few shot example.
         */
        B fewShotExample(FewShotExample fewShotExample);
    }

    /**
     * Расширение {@link FunctionCallback.FunctionInvokingSpec} с доработками для GigaChat.
     *
     * {@link Function} invoking builder interface.
     *
     * @param <I> Function input type.
     * @param <O> Function output type.
     */
    interface GigaChatFunctionInvokingSpec<I, O>
            extends GigaChatCommonCallbackInvokingSpec<GigaChatFunctionInvokingSpec<I, O>> {
        /**
         * Function input type. The input type is used to validate the function input
         * arguments.
         * @see #inputType(ParameterizedTypeReference)
         */
        GigaChatFunctionInvokingSpec<I, O> inputType(Class<?> inputType);

        /**
         * Function input type retaining generic types. The input type is used to validate
         * the function input arguments.
         */
        GigaChatFunctionInvokingSpec<I, O> inputType(ParameterizedTypeReference<?> inputType);

        /**
         * You can provide the Output Type Schema directly. In this case it won't be
         * generated from the inputType.
         */
        GigaChatFunctionInvokingSpec<I, O> outputTypeSchema(String outputTypeSchema);

        /**
         * Function output type.
         */
        GigaChatFunctionInvokingSpec<I, O> outputType(Class<?> inputType);

        /**
         * Function output type retaining generic types.
         */
        GigaChatFunctionInvokingSpec<I, O> outputType(ParameterizedTypeReference<?> inputType);
        /**
         * Builds the {@link FunctionCallback} instance.
         */
        GigaChatFunctionCallback build();
    }

    /**
     * Расширение {@link FunctionCallback.MethodInvokingSpec} с доработками для GigaChat.
     *
     * Method invoking builder interface.
     */
    interface GigaChatMethodInvokingSpec extends GigaChatCommonCallbackInvokingSpec<GigaChatMethodInvokingSpec> {

        /**
         * Optional function name. If not provided the method name is used as the
         * function.
         * @param name Function name. Unique within the model.
         */
        GigaChatMethodInvokingSpec name(String name);

        /**
         * For non-static objects the target object is used to invoke the method.
         * @param methodObject target object where the method is defined.
         */
        GigaChatMethodInvokingSpec targetObject(Object methodObject);

        /**
         * Target class where the method is defined. Used for static methods. For
         * non-static methods the target object is used.
         * @param targetClass method target class.
         */
        GigaChatMethodInvokingSpec targetClass(Class<?> targetClass);

        /**
         * Builds the {@link FunctionCallback} instance.
         */
        GigaChatFunctionCallback build();
    }
}
