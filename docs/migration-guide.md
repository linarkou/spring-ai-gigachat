# Гайд по миграции на Spring AI GigaChat 2.0

## Содержание
- [Разделение класса автоконфигурации `GigaChatAutoConfiguration` на несколько отдельных](#разделение-класса-автоконфигурации-gigachatautoconfiguration-на-несколько-отдельных)
    - [Удаление `GigaChatAutoConfiguration`](#1-удаление-gigachatautoconfiguration)
    - [Перенос properties-классов в подпакет `props`](#2-перенос-properties-классов-в-подпакет-props)
    - [Новая возможность: отключение отдельных моделей](#3-новая-возможность-отключение-отдельных-моделей)
    - [Чек-лист обновления](#чек-лист-обновления)

---

## 1. Изменения в GigaChatOptions

Начиная со Spring AI 2.0, модель конфигурации ChatOptions была переработана и переведена на builder-based API.

Если ранее параметры модели могли изменяться через setters:

```java
GigaChatOptions options = new GigaChatOptions();
options.setTemperature(0.7);
options.setMaxTokens(200);
```

то теперь рекомендуется использовать builder:

```java
GigaChatOptions options = GigaChatOptions.builder()
        .temperature(0.7)
        .maxTokens(200)
        .build();
```

Для поддержки нового механизма объединения настроек Spring AI были реализованы методы:

* `mutate()`
* `clone()`
* `combineWith()`

## 2. Изменения в GigaChatModel

В Spring AI 2.0 были удалены утилиты `ModelOptionsUtils.copyToTarget(...)` и `ModelOptionsUtils.merge(...)`.

В связи с этим логика объединения настроек была перенесена на уровень `GigaChatOptions.Builder` и адаптирована под новый механизм Spring AI.

Пользовательских изменений API не требуется.

## 3. Изменения в конфигурации

Конфигурация Chat-модели была вынесена в отдельный класс `GigaChatChatProperties`.

Это позволило:

* сохранить совместимость с механизмом `@ConfigurationProperties`;
* поддержать новый builder-based API Spring AI;
* разделить runtime-настройки модели и Spring Boot конфигурацию.

### Что нужно сделать

Конфигурация через `chat.options.*` сохранена для обратной совместимости:

**Было:**

```yaml
spring:
  ai:
    gigachat:
      chat:
        options:
          model: GigaChat-2-Max
          temperature: 0.7
          max-tokens: 200
```

**Стало:**

```yaml
spring:
  ai:
    gigachat:
      chat:
        model: GigaChat-2-Max
        temperature: 0.7
        max-tokens: 200
```



## Разделение класса автоконфигурации `GigaChatAutoConfiguration` на несколько отдельных

В этих версиях монолитный класс автоконфигурации был разделён на несколько отдельных,
что даёт возможность более гранулярной настройки и одновременного использования
GigaChat и других провайдеров Spring AI в одном приложении.

Изменения **ломают обратную совместимость** для пользователей, которые:

- явно ссылаются на класс `GigaChatAutoConfiguration` (например, через `@ImportAutoConfiguration` в тестах);
- импортируют классы `GigaChatChatProperties`, `GigaChatEmbeddingProperties`, `GigaChatImageProperties`
  в собственном коде (например, для кастомизации).

Если вы пользуетесь библиотекой только через `application.yml` и не ссылаетесь на эти классы напрямую —
никаких действий не требуется, обновление прозрачно.


### 1. Удаление `GigaChatAutoConfiguration`

Единый класс `chat.giga.springai.autoconfigure.GigaChatAutoConfiguration` **удалён**.
Вместо него теперь четыре независимых класса в пакете `chat.giga.springai.autoconfigure.config`:

| Назначение                        | Новый класс автоконфигурации                                                      |
|-----------------------------------|-----------------------------------------------------------------------------------|
| Создание `GigaChatApi`            | `chat.giga.springai.autoconfigure.config.GigaChatApiAutoConfiguration`            |
| Создание `GigaChatModel`          | `chat.giga.springai.autoconfigure.config.GigaChatChatModelAutoConfiguration`      |
| Создание `GigaChatEmbeddingModel` | `chat.giga.springai.autoconfigure.config.GigaChatEmbeddingModelAutoConfiguration` |
| Создание `GigaChatImageModel`     | `chat.giga.springai.autoconfigure.config.GigaChatImageModelAutoConfiguration`     |

Все четыре класса по-прежнему регистрируются автоматически через
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
никаких ручных действий для подключения не требуется.

#### Что нужно сделать

Если в вашем коде есть прямые ссылки на `GigaChatAutoConfiguration` —
замените их на нужный класс из таблицы выше.

**Было:**

```java
@ImportAutoConfiguration(GigaChatAutoConfiguration.class)
@SpringBootTest
class MyTest { /* ... */ }
```

**Стало:**

```java
@ImportAutoConfiguration({
    GigaChatApiAutoConfiguration.class,
    GigaChatChatModelAutoConfiguration.class,
    GigaChatEmbeddingModelAutoConfiguration.class,
    GigaChatImageModelAutoConfiguration.class
})
@SpringBootTest
class MyTest { /* ... */ }
```

Если вам нужны только конкретные модели — импортируйте только их вместе с
`GigaChatApiAutoConfiguration` (он создаёт `GigaChatApi`, от которого зависят остальные).

---

### 2. Перенос properties-классов в подпакет `props`

Классы конфигурационных свойств были перемещены в подпакет `props`:

| До                                                                         | После                                                                            |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| `chat.giga.springai.autoconfigure.GigaChatChatProperties`                  | `chat.giga.springai.autoconfigure.props.GigaChatChatProperties`                  |
| `chat.giga.springai.autoconfigure.GigaChatEmbeddingProperties`             | `chat.giga.springai.autoconfigure.props.GigaChatEmbeddingProperties`             |
| `chat.giga.springai.autoconfigure.GigaChatImageProperties`                 | `chat.giga.springai.autoconfigure.props.GigaChatImageProperties`                 |

> **Важно:** имена самих классов и префиксы конфигурации (`spring.ai.gigachat.chat`,
> `spring.ai.gigachat.embedding`, `spring.ai.gigachat.image`) **не изменились**, так что
> `application.yml` менять не нужно.

#### Что нужно сделать

Обновите `import`-ы в Java-коде.

**Было:**

```java
import chat.giga.springai.autoconfigure.GigaChatChatProperties;
import chat.giga.springai.autoconfigure.GigaChatEmbeddingProperties;
import chat.giga.springai.autoconfigure.GigaChatImageProperties;
```

**Стало:**

```java
import chat.giga.springai.autoconfigure.props.GigaChatChatProperties;
import chat.giga.springai.autoconfigure.props.GigaChatEmbeddingProperties;
import chat.giga.springai.autoconfigure.props.GigaChatImageProperties;
```

В большинстве IDE достаточно выполнить **Optimize Imports** после обновления зависимости —
старые импорты будут помечены как нерезолвящиеся, и IDE предложит подтянуть классы из нового пакета.

---

### 3. Новая возможность: отключение отдельных моделей

После разделения авто-конфигураций каждая модель регистрируется независимо и подчиняется стандартному
property из Spring AI:

| Модель              | Property для отключения                |
|---------------------|----------------------------------------|
| Чат                 | `spring.ai.model.chat=none`            |
| Эмбеддинги          | `spring.ai.model.embedding=none`       |
| Генерация картинок  | `spring.ai.model.image=none`           |

По умолчанию (`matchIfMissing = true`) все модели включены — поведение для существующих
приложений не меняется.

Это позволяет, например, использовать GigaChat для чата, но другой провайдер — для эмбеддингов:

```yaml
spring:
  ai:
    model:
      chat: gigachat        # GigaChat для чата (значение по умолчанию)
      embedding: openai     # эмбеддинги от OpenAI
      image: none           # генерация изображений отключена
```

Дополнительно каждая `*ModelAutoConfiguration` имеет `@ConditionalOnClass` на свой модельный
класс (`GigaChatModel`, `GigaChatEmbeddingModel`, `GigaChatImageModel`), поэтому конфигурация
не активируется, если соответствующего класса нет в classpath.

---

### Чек-лист обновления

1. Поднимите версию зависимости до `2.x.x`.
   ```xml
   <dependency>
       <groupId>chat.giga</groupId>
       <artifactId>spring-ai-starter-model-gigachat</artifactId>
       <version>2.x.x</version>
   </dependency>
   ```
2. Найдите по проекту использования `GigaChatAutoConfiguration` и замените на нужные классы
   из пакета `chat.giga.springai.autoconfigure.config`.
3. Найдите импорты `chat.giga.springai.autoconfigure.GigaChat*Properties` и поменяйте пакет
   на `chat.giga.springai.autoconfigure.props`.
4. (Опционально) Если используете несколько Spring AI провайдеров — настройте
   `spring.ai.model.{chat|embedding|image}` для выбора нужного.
5. Перекомпилируйте проект и прогоните тесты. Файл `application.yml` менять не нужно.
