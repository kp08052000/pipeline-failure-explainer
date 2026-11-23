package com.example.pfe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Unvalidated output from a provider (AI or offline rules). Mirrors resources/ai/diagnosis-schema.json. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawDiagnosis(
        String verdict,
        String summary,
        String failedStep,
        String category,
        List<RawCause> causes,
        List<RawSuggestion> checks,
        List<RawSuggestion> fixes,
        List<String> missingInformation) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawCause(String title, String explanation, String confidence, List<Integer> evidenceLines) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawSuggestion(String action, String command, String rationale) {}
}
