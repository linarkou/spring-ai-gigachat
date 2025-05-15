package ai.forever.gigachat.autoconfigure;

import ai.forever.gigachat.GigaChatModel;
import ai.forever.gigachat.api.auth.GigaChatApiProperties;
import ai.forever.gigachat.api.chat.GigaChatApi;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.springframework.ai.autoconfigure.chat.observation.ChatObservationAutoConfiguration;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration(
        after = {
            RestClientAutoConfiguration.class,
            SpringAiRetryAutoConfiguration.class,
            ChatObservationAutoConfiguration.class
        })
@EnableConfigurationProperties({GigaChatChatProperties.class, GigaChatChatProperties.class})
@ConditionalOnClass(GigaChatApi.class)
@ConditionalOnProperty(
        prefix = GigaChatChatProperties.CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ImportAutoConfiguration(
        classes = {
            SpringAiRetryAutoConfiguration.class,
            RestClientAutoConfiguration.class,
            WebClientAutoConfiguration.class
        })
public class GigaChatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GigaChatApi gigaChatApi(
            GigaChatApiProperties gigaChatApiProperties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ResponseErrorHandler responseErrorHandler) {
        return new GigaChatApi(gigaChatApiProperties, restClientBuilder, webClientBuilder, responseErrorHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public FunctionCallbackResolver springAiFunctionManager(ApplicationContext context) {
        DefaultFunctionCallbackResolver fcr = new DefaultFunctionCallbackResolver();
        fcr.setApplicationContext(context);
        return fcr;
    }

    @Bean
    @ConditionalOnMissingBean
    public GigaChatModel gigaChatChatModel(
            GigaChatApi gigaChatApi,
            GigaChatChatProperties chatProperties,
            List<FunctionCallback> toolFunctionCallbacks,
            FunctionCallbackResolver functionCallbackResolver,
            RetryTemplate retryTemplate,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention) {
        GigaChatModel gigaChatModel = new GigaChatModel(
                gigaChatApi,
                chatProperties.getOptions(),
                functionCallbackResolver,
                toolFunctionCallbacks,
                retryTemplate,
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

        observationConvention.ifAvailable(gigaChatModel::setObservationConvention);
        return gigaChatModel;
    }

    @Bean
    @ConfigurationProperties(prefix = GigaChatApiProperties.CONFIG_PREFIX)
    public GigaChatApiProperties gigaChatApiProperties() {
        return new GigaChatApiProperties();
    }
}
