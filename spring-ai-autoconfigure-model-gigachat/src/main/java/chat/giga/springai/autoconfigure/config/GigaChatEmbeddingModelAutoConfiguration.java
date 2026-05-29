package chat.giga.springai.autoconfigure.config;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.autoconfigure.props.GigaChatEmbeddingProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
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
@EnableConfigurationProperties(GigaChatEmbeddingProperties.class)
@ConditionalOnClass(GigaChatEmbeddingModel.class)
@ConditionalOnBean(GigaChatApi.class)
@ConditionalOnProperty(
        name = SpringAIModelProperties.EMBEDDING_MODEL,
        havingValue = "gigachat",
        matchIfMissing = true)
@RequiredArgsConstructor
public class GigaChatEmbeddingModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GigaChatEmbeddingModel gigaChatEmbeddingModel(
            final GigaChatApi gigaChatApi,
            final GigaChatEmbeddingProperties gigaChatEmbeddingProperties,
            final ObjectProvider<RetryTemplate> retryTemplateProvider,
            final ObjectProvider<ObservationRegistry> observationRegistry,
            final ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {
        final GigaChatEmbeddingModel gigaChatEmbeddingModel = new GigaChatEmbeddingModel(
                gigaChatApi,
                gigaChatEmbeddingProperties.getOptions(),
                retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

        observationConvention.ifAvailable(gigaChatEmbeddingModel::setObservationConvention);

        return gigaChatEmbeddingModel;
    }
}
