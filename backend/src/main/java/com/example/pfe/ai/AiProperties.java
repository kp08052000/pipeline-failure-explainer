package com.example.pfe.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider       "auto" (AI when a key is set or the preset is local, otherwise offline rules), "openai" or "offline"
 * @param preset         named endpoint preset, see {@link AiConfig#PRESETS}; base-url and model override it
 * @param apiKeyHeader   "Authorization" (Bearer) for most services, "api-key" for Azure OpenAI key auth
 * @param responseFormat "auto", "json_schema" or "json_object"
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String provider,
        String preset,
        String baseUrl,
        String apiKey,
        String apiKeyHeader,
        String model,
        Duration timeout,
        String responseFormat) {}
