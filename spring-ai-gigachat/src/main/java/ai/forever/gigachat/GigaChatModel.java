package ai.forever.gigachat;

import static ai.forever.gigachat.api.chat.GigaChatApi.X_REQUEST_ID;

import ai.forever.gigachat.api.chat.GigaChatApi;
import ai.forever.gigachat.api.chat.completion.CompletionRequest;
import ai.forever.gigachat.api.chat.completion.CompletionResponse;
import ai.forever.gigachat.function.GigaChatFunctionCallback;
import ai.forever.gigachat.metadata.GigaChatUsage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class GigaChatModel extends AbstractToolCallSupport implements ChatModel {
    public static final GigaChatApi.ChatModel DEFAULT_MODEL_NAME = GigaChatApi.ChatModel.GIGA_CHAT;
    public static final DefaultChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION =
            new DefaultChatModelObservationConvention();

    /**
     * The lower-level API for the GigaChat service.
     */
    private final GigaChatApi gigaChatApi;

    /**
     * The default options used for the chat completion requests.
     */
    private final GigaChatOptions defaultOptions;

    /**
     * The retry template used to retry the GigaChat API calls.
     */
    private final RetryTemplate retryTemplate;

    /**
     * The observation registry used for instrumentation.
     */
    private final ObservationRegistry observationRegistry;

    /**
     * Conventions to use for generating observations.
     */
    @Setter
    private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    public GigaChatModel(GigaChatApi gigaChatApi) {
        this(gigaChatApi, GigaChatOptions.builder().model(DEFAULT_MODEL_NAME).build());
    }

    public GigaChatModel(GigaChatApi gigaChatApi, GigaChatOptions defaultOptions) {
        this(gigaChatApi, defaultOptions, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    public GigaChatModel(
            GigaChatApi gigaChatApi,
            GigaChatOptions defaultOptions,
            FunctionCallbackResolver functionCallbackResolver,
            RetryTemplate retryTemplate) {
        this(gigaChatApi, defaultOptions, functionCallbackResolver, List.of(), retryTemplate);
    }

    public GigaChatModel(
            GigaChatApi gigaChatApi,
            GigaChatOptions defaultOptions,
            FunctionCallbackResolver functionCallbackResolver,
            List<FunctionCallback> toolFunctionCallbacks,
            RetryTemplate retryTemplate) {
        this(
                gigaChatApi,
                defaultOptions,
                functionCallbackResolver,
                toolFunctionCallbacks,
                retryTemplate,
                ObservationRegistry.NOOP);
    }

    public GigaChatModel(
            GigaChatApi gigaChatApi,
            GigaChatOptions options,
            FunctionCallbackResolver functionCallbackResolver,
            List<FunctionCallback> toolFunctionCallbacks,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry) {
        super(functionCallbackResolver, options, toolFunctionCallbacks);
        Assert.notNull(gigaChatApi, "gigaChatApi must not be null");
        Assert.notNull(options, "options must not be null");
        Assert.notNull(retryTemplate, "retryTemplate must not be null");
        Assert.notNull(observationRegistry, "observationRegistry must not be null");

        this.gigaChatApi = gigaChatApi;
        this.defaultOptions = options;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        CompletionRequest request = createRequest(prompt, false);

        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .requestOptions(buildRequestOptions(request))
                .build();

        ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                .observation(
                        this.observationConvention,
                        DEFAULT_OBSERVATION_CONVENTION,
                        () -> observationContext,
                        this.observationRegistry)
                .observe(() -> {
                    ResponseEntity<CompletionResponse> completionEntity =
                            this.retryTemplate.execute(ctx -> this.gigaChatApi.chatCompletionEntity(request));
                    CompletionResponse completionResponse = completionEntity.getBody();
                    completionResponse.setId(completionEntity.getHeaders().getFirst(X_REQUEST_ID));
                    ChatResponse chatResponse = toChatResponse(completionResponse, false);
                    observationContext.setResponse(chatResponse);
                    return chatResponse;
                });

        if (!isProxyToolCalls(prompt, this.defaultOptions)
                && response != null
                && this.isToolCall(response, Set.of(CompletionResponse.FinishReason.FUNCTION_CALL))) {
            var toolCallConversation = handleToolCalls(prompt, response);
            return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
        }

        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.deferContextual(contextView -> {
            CompletionRequest request = createRequest(prompt, true);

            ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                    .prompt(prompt)
                    .provider(GigaChatApi.PROVIDER_NAME)
                    .requestOptions(buildRequestOptions(request))
                    .build();

            Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                    .observation(
                            this.observationConvention,
                            DEFAULT_OBSERVATION_CONVENTION,
                            () -> observationContext,
                            this.observationRegistry)
                    .parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
                    .start();

            Flux<CompletionResponse> response =
                    this.retryTemplate.execute(ctx -> this.gigaChatApi.chatCompletionStream(request));

            Flux<ChatResponse> chatResponseFlux = response.switchMap(completionResponse -> {
                        ChatResponse chatResponse = toChatResponse(completionResponse, true);

                        // if (!isProxyToolCalls(prompt, this.defaultOptions) &&
                        //         this.isToolCall(chatResponse, Set.of("tool_use"))) {
                        //     var toolCallConversation = handleToolCalls(prompt, chatResponse);
                        //     return this.stream(new Prompt(toolCallConversation, prompt.getOptions()));
                        // }

                        return Mono.just(chatResponse);
                    })
                    .doOnError(observation::error)
                    .doFinally(s -> observation.stop())
                    .contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

            return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
        });
    }

    private CompletionRequest createRequest(Prompt prompt, boolean stream) {
        List<CompletionRequest.Message> messages = prompt.getInstructions().stream()
                .map(message -> {
                    if (message instanceof UserMessage userMessage) {
                        return List.of(
                                new CompletionRequest.Message(CompletionRequest.Role.user, userMessage.getContent()));
                    } else if (message instanceof SystemMessage systemMessage) {
                        return List.of(new CompletionRequest.Message(
                                CompletionRequest.Role.system, systemMessage.getContent()));
                    } else if (message instanceof AssistantMessage assistantMessage) {
                        CompletionRequest.Message.MessageBuilder messageBuilder = CompletionRequest.Message.builder()
                                .role(CompletionRequest.Role.assistant)
                                .content(assistantMessage.getContent());
                        if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                            if (assistantMessage.getToolCalls().size() > 1) {
                                log.warn(
                                        "Too many function calls, only first one used: {}",
                                        assistantMessage.getToolCalls());
                            }
                            AssistantMessage.ToolCall toolCall =
                                    assistantMessage.getToolCalls().get(0);
                            messageBuilder
                                    .functionsStateId(toolCall.id())
                                    .functionCall(new CompletionResponse.FunctionCall()
                                            .setName(toolCall.name())
                                            .setArguments(toolCall.arguments()));
                        }
                        return List.of(messageBuilder.build());
                    } else if (message instanceof ToolResponseMessage toolResponseMessage) {
                        // по идее для гигачата тут всегда должен быть только один результат вызова функции
                        return toolResponseMessage.getResponses().stream()
                                .map(toolResponse -> CompletionRequest.Message.builder()
                                        .role(CompletionRequest.Role.function)
                                        .content(toolResponse.responseData())
                                        .name(toolResponse.name())
                                        .build())
                                .toList();
                    } else {
                        throw new IllegalStateException("Unexpected message type: " + message);
                    }
                })
                .flatMap(List::stream)
                .toList();

        var request =
                CompletionRequest.builder().messages(messages).stream(stream).build();

        Set<String> functionsForThisRequest = new HashSet<>();

        if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
            functionsForThisRequest.addAll(this.defaultOptions.getFunctions());
        }

        request = ModelOptionsUtils.merge(request, this.defaultOptions, CompletionRequest.class);

        if (prompt.getOptions() != null) {
            GigaChatOptions updatedRuntimeOptions;

            if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
                updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(
                        functionCallingOptions, FunctionCallingOptions.class, GigaChatOptions.class);
            } else {
                updatedRuntimeOptions =
                        ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class, GigaChatOptions.class);
            }

            functionsForThisRequest.addAll(this.runtimeFunctionCallbackConfigurations(updatedRuntimeOptions));

            request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, CompletionRequest.class);
        }

        // Add the enabled functions definitions to the request's tools parameter.
        if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
            request.setFunctionCall("auto");
            request.setFunctions(this.getFunctionDescriptions(functionsForThisRequest));
        }
        return request;
    }

    private List<CompletionRequest.FunctionDescription> getFunctionDescriptions(Set<String> functionNames) {
        return this.resolveFunctionCallbacks(functionNames).stream()
                .map(functionCallback -> {
                    if (functionCallback instanceof GigaChatFunctionCallback gigaChatFunctionCallback) {
                        return new CompletionRequest.FunctionDescription(
                                gigaChatFunctionCallback.getName(),
                                gigaChatFunctionCallback.getDescription(),
                                gigaChatFunctionCallback.getInputTypeSchema(),
                                gigaChatFunctionCallback.getFewShotExamples().stream()
                                        .map(fewShotExample -> new CompletionRequest.FewShotExample(
                                                fewShotExample.request(), fewShotExample.params()))
                                        .toList(),
                                gigaChatFunctionCallback.getOutputTypeSchema());
                    } else {
                        return new CompletionRequest.FunctionDescription(
                                functionCallback.getName(),
                                functionCallback.getDescription(),
                                functionCallback.getInputTypeSchema(),
                                null,
                                null);
                    }
                })
                .toList();
    }

    private ChatResponse toChatResponse(CompletionResponse completionResponse, boolean streaming) {
        if (completionResponse == null) {
            log.warn("Null completion response");
            return new ChatResponse(List.of());
        }

        List<Generation> generations = completionResponse.getChoices().stream()
                .map(choice -> buildGeneration(completionResponse.getId(), choice, streaming))
                .toList();
        return new ChatResponse(generations, extractMetadataFrom(completionResponse));
    }

    private Generation buildGeneration(String id, CompletionResponse.Choice choice, boolean streaming) {
        CompletionResponse.MessagesRes message = streaming ? choice.getDelta() : choice.getMessage();
        String finishReason = choice.getFinishReason() != null ? choice.getFinishReason() : "";
        Map<String, Object> metadata = Map.of(
                "id",
                id,
                "index",
                choice.getIndex(),
                "role",
                message.getRole() != null ? message.getRole().name() : "",
                "finishReason",
                finishReason);
        List<AssistantMessage.ToolCall> toolCalls;
        if (CompletionResponse.FinishReason.FUNCTION_CALL.equals(finishReason)) {
            String functionsStateId = message.getFunctionsStateId();
            AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                    functionsStateId,
                    "function",
                    message.getFunctionCall().getName(),
                    message.getFunctionCall().getArguments());
            toolCalls = List.of(toolCall);
        } else {
            toolCalls = List.of();
        }
        var assistantMessage = new AssistantMessage(message.getContent(), metadata, toolCalls);
        var generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(choice.getFinishReason())
                .build();
        return new Generation(assistantMessage, generationMetadata);
    }

    private ChatResponseMetadata extractMetadataFrom(CompletionResponse completionResponse) {
        Assert.notNull(completionResponse, "GigaChat CompletionResponse must not be null");
        Usage usage = (completionResponse.getUsage() != null)
                ? GigaChatUsage.from(completionResponse.getUsage())
                : new EmptyUsage();
        return ChatResponseMetadata.builder()
                .id(completionResponse.getId())
                .model(completionResponse.getModel())
                .usage(usage)
                .keyValue("created", completionResponse.getCreated())
                .keyValue("object", completionResponse.getObject())
                .build();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return this.defaultOptions.copy();
    }

    private ChatOptions buildRequestOptions(CompletionRequest request) {
        return ChatOptions.builder()
                .model(request.getModel().getName())
                .maxTokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .frequencyPenalty(request.getRepetitionPenalty())
                .build();
    }
}
