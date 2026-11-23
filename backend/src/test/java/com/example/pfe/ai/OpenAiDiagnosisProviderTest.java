package com.example.pfe.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pfe.model.RawDiagnosis;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OpenAiDiagnosisProviderTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicInteger overloadedResponses = new AtomicInteger();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private OpenAiDiagnosisProvider start(int status, String responseBody) throws IOException {
        return start(status, responseBody, AiEndpoint.ResponseFormat.AUTO, false);
    }

    private OpenAiDiagnosisProvider start(int status, String responseBody, AiEndpoint.ResponseFormat format,
                                          boolean rejectSchema) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBody.set(body);
            requestCount.incrementAndGet();
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            boolean reject = rejectSchema && body.contains("\"json_schema\"");
            boolean overloaded = overloadedResponses.getAndDecrement() > 0;
            byte[] bytes = (reject ? "{\"error\":\"response_format json_schema not supported\"}" : responseBody)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(overloaded ? 503 : reject ? 400 : status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        return new OpenAiDiagnosisProvider(
                new AiEndpoint("test:test-model", baseUrl, "test-key", "Authorization", "test-model", Duration.ofSeconds(5), format),
                mapper);
    }

    private String chatResponse(String content) {
        return mapper.writeValueAsString(
                Map.of("choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content)))));
    }

    private static final String MINIMAL = """
            {"verdict":"DIAGNOSED","summary":"s","failedStep":null,"category":"dependency","extra":1,
             "causes":[{"title":"t","explanation":"e","confidence":"HIGH","evidenceLines":[2]}],
             "checks":[],"fixes":[],"missingInformation":[]}""";

    @Test
    void fallsBackToJsonModeWhenSchemaIsRejectedAndToleratesMarkdownFences() throws IOException {
        OpenAiDiagnosisProvider provider = start(200, chatResponse("```json\n" + MINIMAL + "\n```"),
                AiEndpoint.ResponseFormat.AUTO, true);

        RawDiagnosis first = provider.diagnose("L1| a\nL2| b\n", List.of("a", "b"));
        assertThat(first.causes()).singleElement().satisfies(c -> assertThat(c.evidenceLines()).containsExactly(2));
        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(mapper.readTree(requestBody.get()).path("response_format").path("type").asString()).isEqualTo("json_object");

        provider.diagnose("L1| a\n", List.of("a"));
        assertThat(requestCount.get()).as("remembers the fallback").isEqualTo(3);
    }

    @Test
    void retriesTransientOverloadThenSucceeds() throws IOException {
        overloadedResponses.set(2);
        OpenAiDiagnosisProvider provider = start(200, chatResponse(MINIMAL), AiEndpoint.ResponseFormat.JSON_SCHEMA, false);
        provider.retryBaseDelayMillis = 1;

        assertThat(provider.diagnose("L1| a\nL2| b\n", List.of("a", "b")).verdict()).isEqualTo("DIAGNOSED");
        assertThat(requestCount.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterMaxRetries() throws IOException {
        overloadedResponses.set(10);
        OpenAiDiagnosisProvider provider = start(200, chatResponse(MINIMAL), AiEndpoint.ResponseFormat.JSON_SCHEMA, false);
        provider.retryBaseDelayMillis = 1;

        assertThatThrownBy(() -> provider.diagnose("L1| x\n", List.of("x")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("503");
        assertThat(requestCount.get()).isEqualTo(3);
    }

    @Test
    void jsonSchemaModeDoesNotFallBack() throws IOException {
        OpenAiDiagnosisProvider provider = start(200, chatResponse(MINIMAL), AiEndpoint.ResponseFormat.JSON_SCHEMA, true);

        assertThatThrownBy(() -> provider.diagnose("L1| x\n", List.of("x")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("400");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void sendsStrictSchemaRequestAndParsesStructuredContent() throws IOException {
        String content = mapper.writeValueAsString(new RawDiagnosis("DIAGNOSED", "npm conflict", "npm ci", "dependency",
                List.of(new RawDiagnosis.RawCause("Peer conflict", "x", "HIGH", List.of(2))), List.of(), List.of(), List.of()));
        String response = mapper.writeValueAsString(
                Map.of("choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content)))));

        RawDiagnosis result = start(200, response).diagnose("L1| a\nL2| npm error code ERESOLVE\n", List.of("a", "npm error code ERESOLVE"));

        assertThat(result.causes()).singleElement().satisfies(c -> assertThat(c.evidenceLines()).containsExactly(2));
        assertThat(authHeader.get()).isEqualTo("Bearer test-key");
        JsonNode sent = mapper.readTree(requestBody.get());
        assertThat(sent.path("model").asString()).isEqualTo("test-model");
        assertThat(sent.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(sent.path("messages").path(1).path("content").asString()).contains("L2| npm error code ERESOLVE", "untrusted");
    }

    @Test
    void upstreamErrorBecomesAiProviderException() throws IOException {
        OpenAiDiagnosisProvider provider = start(429, "{\"error\":{\"message\":\"rate limited\"}}");

        assertThatThrownBy(() -> provider.diagnose("L1| x\n", List.of("x")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("429");
    }
}
