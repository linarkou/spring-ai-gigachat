package chat.giga.springai.autoconfigure.config;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.autoconfigure.props.GigaChatChatModelProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class GigaChatChatModelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "spring.ai.gigachat.auth.bearer.api-key=test",
                    "spring.ai.model.chat=gigachat"
            )
            .withConfiguration(AutoConfigurations.of(
                    GigaChatApiAutoConfiguration.class,
                    GigaChatChatModelAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration.class,
                    org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration.class
            ));

    @Test
    @DisplayName("Проверяет создание GigaChatModel и GigaChatChatProperties")
    void chatModelBeanCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GigaChatModel.class);
            assertThat(context).hasSingleBean(GigaChatChatModelProperties.class);
        });
    }

    @Test
    @DisplayName("Проверяет кастомные настройки Chat модели")
    void customChatPropertiesTest() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.gigachat.chat.options.model=GigaChat-2-Max",
                        "spring.ai.gigachat.chat.options.temperature=0.7",
                        "spring.ai.gigachat.chat.options.top-p=0.5",
                        "spring.ai.gigachat.chat.options.max-tokens=200",
                        "spring.ai.gigachat.chat.options.repetition-penalty=2.0"
                )
                .run(context -> {
                    GigaChatChatModelProperties chatProperties = context.getBean(GigaChatChatModelProperties.class);
                    assertThat(chatProperties.getOptions().getModel()).isEqualTo("GigaChat-2-Max");
                    assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.7);
                    assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.5);
                    assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(200);
                    assertThat(chatProperties.getOptions().getRepetitionPenalty()).isEqualTo(2.0);
                });
    }

    @ParameterizedTest
    @CsvSource({
            "gigachat, true",
            "openai, false",
            ", true"
    })
    @DisplayName("Условное создание модели в зависимости от spring.ai.model.chat")
    void conditionalOnChatModelProperty(String propertyValue, boolean beanShouldExist) {
        ApplicationContextRunner runner = contextRunner;
        if (propertyValue != null) {
            runner = runner.withPropertyValues(SpringAIModelProperties.CHAT_MODEL + "=" + propertyValue);
        }
        runner.run(context -> {
            if (beanShouldExist) {
                assertThat(context).hasSingleBean(GigaChatModel.class);
            } else {
                assertThat(context).doesNotHaveBean(GigaChatModel.class);
            }
        });
    }
}