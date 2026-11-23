package com.example.pfe.ai;

import java.time.Duration;

/** Fully resolved connection settings for an OpenAI-compatible endpoint. */
public record AiEndpoint(
        String name,
        String baseUrl,
        String apiKey,
        String apiKeyHeader,
        String model,
        Duration timeout,
        ResponseFormat responseFormat) {

    /** JSON_SCHEMA = strict structured output; JSON_OBJECT = plain JSON mode; AUTO = schema, falling back to JSON mode. */
    public enum ResponseFormat { AUTO, JSON_SCHEMA, JSON_OBJECT }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
