package chat.giga.springai.autoconfigure.props;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.GigaChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(GigaChatChatModelProperties.CONFIG_PREFIX)
public class GigaChatChatModelProperties {

    public static final String CONFIG_PREFIX = "spring.ai.gigachat.chat";

    @NestedConfigurationProperty
    private GigaChatOptions options =
            GigaChatOptions.builder().model(GigaChatModel.DEFAULT_MODEL_NAME).build();

    public void setOptions(GigaChatOptions options) {
        this.options = options;
    }

    public GigaChatOptions getOptions() {
        return this.options;
    }
}
