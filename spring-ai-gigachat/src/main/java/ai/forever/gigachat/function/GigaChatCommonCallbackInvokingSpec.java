package ai.forever.gigachat.function;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.model.function.DefaultCommonCallbackInvokingSpec;
import org.springframework.util.Assert;

/**
 * Расширение {@link DefaultCommonCallbackInvokingSpec<B>} с доработками под GigaChat.
 * @param <B>
 */
public class GigaChatCommonCallbackInvokingSpec<
                B extends GigaChatFunctionCallback.GigaChatCommonCallbackInvokingSpec<B>>
        extends DefaultCommonCallbackInvokingSpec<B>
        implements GigaChatFunctionCallback.GigaChatCommonCallbackInvokingSpec<B> {

    /**
     * Объекты с парами запрос_пользователя-параметры_функции,
     * которые будут служить модели примерами ожидаемого результата.
     */
    protected List<GigaChatFunctionCallback.FewShotExample> fewShotExamples = new ArrayList<>();

    public List<GigaChatFunctionCallback.FewShotExample> getFewShotExamples() {
        return fewShotExamples;
    }

    @Override
    public B fewShotExample(GigaChatFunctionCallback.FewShotExample fewShotExample) {
        Assert.notNull(fewShotExample, "FewShotExample must not be null");
        fewShotExamples.add(fewShotExample);
        return (B) this;
    }
}
