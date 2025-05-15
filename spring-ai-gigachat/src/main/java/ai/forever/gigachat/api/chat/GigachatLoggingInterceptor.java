package ai.forever.gigachat.api.chat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

@RequiredArgsConstructor
@Slf4j
public class GigachatLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Request body: {}", new String(body));
            ClientHttpResponse response = execution.execute(request, body);
            response = new BufferingClientHttpResponseWrapper(response);
            String requestId = response.getHeaders().getFirst(GigaChatApi.X_REQUEST_ID);
            log.debug(
                    "Response body ({}={}): {}",
                    GigaChatApi.X_REQUEST_ID,
                    requestId,
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
            return response;
        } else {
            return execution.execute(request, body);
        }
    }

    /**
     * Нужен для кэширования тела ответа,
     * т.к. по дефолту тело ответа имеет тип InputStream и может быть вычитано только один раз
     */
    public static class BufferingClientHttpResponseWrapper implements ClientHttpResponse {

        private final ClientHttpResponse response;
        private byte[] body;

        public BufferingClientHttpResponseWrapper(ClientHttpResponse response) {
            this.response = response;
        }

        @Override
        public InputStream getBody() throws IOException {
            if (body == null) {
                body = StreamUtils.copyToByteArray(response.getBody());
            }
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return this.response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return this.response.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.response.getHeaders();
        }

        @Override
        public void close() {
            this.response.close();
        }
    }
}
