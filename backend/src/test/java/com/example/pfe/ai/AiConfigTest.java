package com.example.pfe.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiConfigTest {

    private static AiProperties props(String provider, String preset, String apiKey, String model) {
        return new AiProperties(provider, preset, null, apiKey, null, model, Duration.ofSeconds(30), null);
    }

    @Test
    void noKeyMeansOfflineRules() {
        assertThat(AiConfig.resolve(props("auto", "openai", null, null), name -> null)).isNull();
    }

    @Test
    void presetSuppliesUrlModelAndReadsItsOwnKeyVariable() {
        AiEndpoint e = AiConfig.resolve(props("auto", "gemini", null, null), Map.of("GEMINI_API_KEY", "k")::get);

        assertThat(e.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai");
        assertThat(e.model()).isEqualTo("gemini-flash-latest");
        assertThat(e.apiKey()).isEqualTo("k");
        assertThat(e.name()).isEqualTo("gemini:gemini-flash-latest");
        assertThat(e.responseFormat()).isEqualTo(AiEndpoint.ResponseFormat.AUTO);
    }

    @Test
    void localPresetNeedsNoKeyAndModelCanBeOverridden() {
        AiEndpoint e = AiConfig.resolve(props("auto", "ollama", null, "llama3.1:8b"), name -> null);

        assertThat(e.baseUrl()).isEqualTo("http://localhost:11434/v1");
        assertThat(e.model()).isEqualTo("llama3.1:8b");
        assertThat(e.hasApiKey()).isFalse();
    }

    @Test
    void invalidConfigurationFailsFast() {
        assertThatThrownBy(() -> AiConfig.resolve(props("auto", "nope", "k", null), name -> null))
                .hasMessageContaining("Unknown AI_PRESET");
        assertThatThrownBy(() -> AiConfig.resolve(props("auto", "lmstudio", null, null), name -> null))
                .hasMessageContaining("AI_MODEL");
        assertThatThrownBy(() -> AiConfig.resolve(props("openai", "github", null, null), name -> null))
                .hasMessageContaining("GITHUB_TOKEN");
    }
}
