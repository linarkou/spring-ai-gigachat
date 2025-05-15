# spring-ai-gigachat-example

Примеры использования Spring AI для интеграции с GigaChat.

## Простые примеры с разными системными промптами

[QuestionAnswerController](src/main/java/ai/forever/gigachat/example/QuestionAnswerController.java)

```shell
curl localhost:8080/answer -d "Кто ты?" -H "content-type:application/json"
curl localhost:8080/developer/answer -d "Кто ты?" -H "content-type:application/json"
curl localhost:8080/math/answer -d "Кто ты?" -H "content-type:application/json"
```

## Примеры с сохранением контекста

[ChatController](src/main/java/ai/forever/gigachat/example/ChatController.java)

```shell
curl "localhost:8080/chat?chatId=123" -d "Как установить GigaCode в IntelliJ IDEA?" -H "content-type:application/json"
# после второго запроса GigaChat должен понять, что ранее речь шла про GigaCode, и дать инструкцию по его использованию
curl "localhost:8080/chat?chatId=123" -d "Как его использовать?" -H "content-type:application/json"
# если выполнить такой же запрос с другим chatId, то GigaChat не поймет, о чем речь.
curl "localhost:8080/chat?chatId=456" -d "Как его использовать?" -H "content-type:application/json"
```

## Примеры с запросом ответа в формате json и конвертацией в POJO

[StructuredEntityController](src/main/java/ai/forever/gigachat/example/StructuredEntityController.java)

```shell
curl "localhost:8080/actor-films" -d "Назови 5 популярных фильмов с Сергеем Безруковым" -H "content-type:application/json"
```

Пример ответа: `{"actor":"Сергей Безруков","movies":["Бригада","Есенин","Адмиралъ","Высоцкий. Спасибо, что живой","Участок"]}`

## Примеры со стримингом ответа

[StreamAnswerController](src/main/java/ai/forever/gigachat/example/StreamAnswerController.java)

```shell
# флаг -N позволяет выводить ответ сразу как он пришел, без буфферизации
curl -N "localhost:8080/stream/answer" -d "Как интегрироваться с GigaChat?" -H "content-type:application/json"
```

В результате ответ модели на экране будет появляться не весь сразу, а небольшими кусочками.

## Примеры с вызовом внешних функций

[WeatherFunctionController](src/main/java/ai/forever/gigachat/example/WeatherFunctionController.java)

Функция определения температуры описана в нашем коде (для простоты выдает рандомную температуру).

Гигачат определяет, что надо вызвать эту функцию, и в ответе передает параметры для её вызова;
затем Гигачат на основе результатов вызова функции генерирует ответ.

Различия во внутренней работе api версий v1 и v2 описаны в [коде WeatherFunctionController](src/main/java/ai/forever/gigachat/example/WeatherFunctionController.java).

```shell
curl localhost:8080/function/v1/weather -d "Какая температура в Казани?" -H "content-type:application/json"
curl localhost:8080/function/v2/weather -d "Сколько градусов в Спб?" -H "content-type:application/json"
```

