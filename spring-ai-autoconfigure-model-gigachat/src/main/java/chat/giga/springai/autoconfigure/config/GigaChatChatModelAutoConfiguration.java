package chat.giga.springai.autoconfigure.config;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.autoconfigure.props.GigaChatChatModelProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;

@AutoConfiguration(
        after = {
                GigaChatApiAutoConfiguration.class,
                SpringAiRetryAutoConfiguration.class
        }
)
@EnableConfigurationProperties(GigaChatChatModelProperties.class)
@ConditionalOnClass(GigaChatModel.class)
@ConditionalOnBean(GigaChatApi.class)
@ConditionalOnProperty(
        name = SpringAIModelProperties.CHAT_MODEL,
        havingValue = "gigachat",
        matchIfMissing = true)
@RequiredArgsConstructor
public class GigaChatChatModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GigaChatModel gigaChatChatModel(
            final GigaChatApi gigaChatApi,
            final GigaChatChatModelProperties chatProperties,
            final ObjectProvider<RetryTemplate> retryTemplateProvider,
            final ObjectProvider<ToolCallingManager> toolCallingManagerProvider,
            final ObjectProvider<ObservationRegistry> observationRegistry,
            final ObjectProvider<ChatModelObservationConvention> observationConvention,
            final ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
            final GigaChatInternalProperties internalProperties) {
        final GigaChatModel gigaChatModel = GigaChatModel.builder()
                .gigaChatApi(gigaChatApi)
                .defaultOptions(chatProperties.getOptions())
                .retryTemplate(retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
                .toolCallingManager(
                        toolCallingManagerProvider.getIfAvailable(() -> GigaChatModel.DEFAULT_TOOL_CALLING_MANAGER))
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .toolExecutionEligibilityPredicate(
                        toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
                .internalProperties(internalProperties)
                .build();

        observationConvention.ifAvailable(gigaChatModel::setObservationConvention);
        return gigaChatModel;
    }
}
