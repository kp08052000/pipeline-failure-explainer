package com.example.pfe.model;

import java.util.List;

/** Validated diagnosis returned to the UI. Every cause is grounded in real log lines. */
public record Diagnosis(
        Verdict verdict,
        String summary,
        String failedStep,
        String category,
        List<Cause> causes,
        List<Suggestion> checks,
        List<Suggestion> fixes,
        List<String> missingInformation,
        List<String> warnings,
        String provider,
        int lineCount,
        int redactionCount) {

    public enum Verdict { DIAGNOSED, INSUFFICIENT_EVIDENCE }

    public enum Confidence { HIGH, MEDIUM, LOW }

    public enum Risk { SAFE, CAUTION }

    public record Cause(String title, String explanation, Confidence confidence, List<Evidence> evidence) {}

    public record Evidence(int line, String text) {}

    /** A check or fix. Commands are display-only text and are never executed by this service. */
    public record Suggestion(String action, String command, String rationale, Risk risk, String riskNote) {}
}
