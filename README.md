# spring-ai-starter-gigachat

## Быстрый старт

Для работы с библиотекой вам понадобится ключ авторизации API.

Чтобы получить ключ авторизации:
1. Создайте проект **GigaChat API** в личном кабинете Studio.
2. В интерфейсе проекта, в левой панели выберите раздел **Настройки API**.
3. Нажмите кнопку **Получить ключ**.

В открывшемся окне скопируйте и сохраните значение поля **Client Secret**.
Этот ключ отображается только один раз и не хранится в личном кабинете.
При компрометации или утере ключа авторизации вы можете сгенерировать его повторно.

1) Добавьте в ваш pom.xml или settings.xml репозиторий [Spring Milestones](https://repo.spring.io/milestone/)
для доступа к Spring AI.

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone/</url>
    </repository>
</repositories>
```

2) Подключите в ваш pom.xml зависимость

```xml
<dependency>
    <groupId>ai.forever.gigachat</groupId>
    <artifactId>spring-ai-gigachat-spring-boot-starter</artifactId>
    <version>1.0.0-M5-SNAPSHOT</version>
</dependency>
```

3) Пропишите параметры подключения к GigaChat в application.yml:

```yaml
spring:
  ai:
    gigachat:
      client-id: <ваш_client_id>  # Можно посмотреть в личном кабинете GigaChat в разделе "Настройки API" в вашем проекте
      client-secret: <ваш_client_secret>  # Ваш Client Secret
      scope: GIGACHAT_API_PERS  # доступны также GIGACHAT_API_B2B, GIGACHAT_API_CORP
      unsafe-ssl: true
```

4) Добавьте в код:

```java
@Component
public class TestGigachat {
    private ChatClient chatClient;

    @Autowired
    public TestGigachat(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.helloGigachat();
    }
    
    public void helloGigachat() {
        System.out.println(chatClient.prompt().user("Привет, Гигачат!").call().content());
    }
```

## Конфигурация

В application.yml можно задать дефолтные параметры для запроса к API GigaChat,
а также параметры для подключения и авторизации.

Описание параметров можно найти в [официальной документации](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat).

```yaml
spring:
  ai:
    gigachat:
      base-url: <url для доступа к GigaChat>          # По дефолту используется https://gigachat.devices.sberbank.ru/api/v1/
      auth-url: <url для авторизации запросов к API>  # По дефолту используется https://ngw.devices.sberbank.ru:9443/api/v2/oauth
      client-id: <ваш_client_id>                      # Ваш Client ID. Можно посмотреть в личном кабинете GigaChat в разделе "Настройки API" в вашем проекте
      client-secret: <ваш_client_secret>              # Ваш Client Secret.
      scope: GIGACHAT_API_PERS                        # Доступны также GIGACHAT_API_B2B, GIGACHAT_API_CORP
      unsafe-ssl: true                                # Отключение проверки ssl-сертификатов. Для обращения к GigaChat API нужно установить корневой сертификат НУЦ Минцифры.
      chat:
        options:
          # дефолтные параметры для обращения к API GigaChat.
          model: GIGA_CHAT       # еще доступны GIGA_CHAT_PREVIEW, GIGA_CHAT_PLUS, GIGA_CHAT_PLUS_PREVIEW, GIGA_CHAT_PRO, GIGA_CHAT_PRO_PREVIEW, GIGA_CHAT_MAX, GIGA_CHAT_MAX_PREVIEW
          temperature: 0.5       # null по дефолту
          top-p: 0.5             # null по дефолту
          max-tokens: 200        # null по дефолту
          repetition-penalty: 1  # null по дефолту 
          update-interval: 0     # null по дефолту
```

## Способы авторизации

### По Client ID + Client Secret

Пример application.yml для авторизации по Client ID и Client Secret:

```yaml
spring:
  ai:
    gigachat:
      scope: GIGACHAT_API_PERS
      client-id: <ваш_client_id>             # Можно посмотреть в личном кабинете GigaChat в разделе "Настройки API" в вашем проекте
      client-secret: <ваш_client_secret>     # Можно посмотреть в личном кабинете GigaChat в разделе "Настройки API" в вашем проекте
```

### С помощью TLS-сертификатов

Пример application.yml для подключения к GigaChat :

```yaml
spring:
  ai:
    gigachat:
      scope: GIGACHAT_API_CORP
      client-key: file:/path/to/tls.key         # Путь до вашего сертификата. Если у вас терминация TLS настроена на Egress-gateway, то можно пропустить этот параметр
      client-certificate: file:/path/to/tls.crt # Путь до вашего сертификата. Если у вас терминация TLS настроена на Egress-gateway, то можно пропустить этот параметр
```

## [Примеры](./spring-ai-gigachat-example/README.md)

