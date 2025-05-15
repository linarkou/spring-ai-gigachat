package ai.forever.gigachat.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ai.forever.gigachat.function.GigaChatFunctionCallback;
import ai.forever.gigachat.function.GigaChatFunctionCallback.FewShotExample;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class WeatherFunctionController {
    private final ChatClient chatClient;

    public WeatherFunctionController(ChatClient.Builder chatClientBuilder) {
        this.chatClient =
                chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
    }

    // request
    record City(@JsonPropertyDescription("Название города") String name) {}

    // response
    record CityTemperature(@JsonPropertyDescription("Температура в градусах цельсия") Integer temperature) {}

    /**
     * Пример использования стандартного для Spring AI функционала вызова внешних функций через FunctionCallback
     */
    @PostMapping("function/v1/weather")
    public String weatherDefault(@RequestBody String question) {
        return chatClient
                .prompt(question)
                .functions(FunctionCallback.builder()
                        .function("getTemperatureFunction", (City city) -> {
                            String answer =
                                    "В " + city.name() + " " + (int) ((Math.random() - 0.5) * 50) + " градусов цельсия";
                            log.info(answer);
                            // если результат работы функции - строка,
                            // то обязательно обернуть ее в кавычки, чтобы получился валидный JSON
                            return "\"" + answer + "\"";
                        })
                        .description("Функция получения погоды в градусах цельсия для конкретного города")
                        .inputType(City.class)
                        .build())
                .call()
                .content();
    }

    /**
     * Пример использования вызова внешних функций через кастомный GigaChatFunctionCallback
     */
    @PostMapping("function/v2/weather")
    public String weatherGigaChatCustom(@RequestBody String question) {
        return chatClient
                .prompt(question)
                .functions(GigaChatFunctionCallback.builder()
                        .function("getTemperatureFunction", (City city) -> {
                            int temperature = (int) ((Math.random() - 0.5) * 50);
                            // return temperature;
                            return new CityTemperature(temperature);
                        })
                        .description("Функция получения погоды в градусах цельсия для конкретного города")
                        .inputType(City.class)
                        .fewShotExample(new FewShotExample("Какая температура в Питере?", new City("Санкт-Петербург")))
                        .fewShotExample(new FewShotExample("Сколько градусов в Москве?", new City("Москва")))
                        // .outputType(Integer.class) - не надо так
                        // используйте outputType только со сложными(составными) типами данных
                        .outputType(CityTemperature.class)
                        .build())
                .call()
                .content();
    }
}
