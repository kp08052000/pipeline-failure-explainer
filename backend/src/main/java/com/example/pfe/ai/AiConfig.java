package com.example.pfe.ai;

import com.example.pfe.ai.AiEndpoint.ResponseFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * @param keyEnv env var read when AI_API_KEY is not set (null = none)
     * @param local  runs on this machine, so no API key is required
     */
    record Preset(String baseUrl, String defaultModel, String keyEnv, boolean local) {}

    // Default models are starting points; free-tier model names change, override with AI_MODEL.
    static final Map<String, Preset> PRESETS = Map.of(
            "openai", new Preset("https://api.openai.com/v1", "gpt-4.1-mini", "OPENAI_API_KEY", false),
            "ollama", new Preset("http://localhost:11434/v1", "qwen2.5:7b", null, true),
            "lmstudio", new Preset("http://localhost:1234/v1", null, null, true),
            "github", new Preset("https://models.github.ai/inference", "openai/gpt-4.1-mini", "GITHUB_TOKEN", false),
            "gemini", new Preset("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-flash-latest", "GEMINI_API_KEY", false),
            "groq", new Preset("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "GROQ_API_KEY", false),
            "openrouter", new Preset("https://openrouter.ai/api/v1", null, "OPENROUTER_API_KEY", false),
            "mistral", new Preset("https://api.mistral.ai/v1", "mistral-small-latest", "MISTRAL_API_KEY", false));

    @Bean
    DiagnosisProvider diagnosisProvider(AiProperties props, ObjectMapper mapper) {
        AiEndpoint endpoint = resolve(props, System::getenv);
        DiagnosisProvider provider = endpoint == null ? new OfflineRulesProvider() : new OpenAiDiagnosisProvider(endpoint, mapper);
        log.info("Diagnosis provider: {}", provider.name());
        return provider;
    }

    /** Returns the AI endpoint to use, or null for the offline rules provider. */
    static AiEndpoint resolve(AiProperties props, UnaryOperator<String> env) {
        String presetName = lower(props.preset(), "openai");
        Preset preset = PRESETS.get(presetName);
        if (preset == null) {
            throw new IllegalStateException("Unknown AI_PRESET '" + presetName + "'. Use one of " + PRESETS.keySet());
        }

        String apiKey = firstNonBlank(props.apiKey(), preset.keyEnv() == null ? null : env.apply(preset.keyEnv()));
        boolean canCall = preset.local() || apiKey != null;

        String mode = lower(props.provider(), "auto");
        boolean useAi = switch (mode) {
            case "offline" -> false;
            case "openai" -> {
                if (!canCall) {
                    throw new IllegalStateException("AI_PROVIDER=openai requires AI_API_KEY"
                            + (preset.keyEnv() == null ? "" : " or " + preset.keyEnv()));
                }
                yield true;
            }
            case "auto" -> canCall;
            default -> throw new IllegalStateException("Unknown AI_PROVIDER '" + mode + "'. Use auto, openai or offline");
        };
        if (!useAi) {
            return null;
        }

        String model = firstNonBlank(props.model(), preset.defaultModel());
        if (model == null) {
            throw new IllegalStateException("Set AI_MODEL for preset '" + presetName + "'");
        }
        ResponseFormat format = ResponseFormat.valueOf(lower(props.responseFormat(), "auto").toUpperCase(Locale.ROOT));
        return new AiEndpoint(
                presetName + ":" + model,
                firstNonBlank(props.baseUrl(), preset.baseUrl()),
                apiKey,
                firstNonBlank(props.apiKeyHeader(), "Authorization"),
                model,
                props.timeout() == null ? Duration.ofSeconds(60) : props.timeout(),
                format);
    }

    private static String lower(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b == null || b.isBlank() ? null : b.trim();
    }
}
