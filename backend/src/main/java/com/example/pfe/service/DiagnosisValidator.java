package com.example.pfe.service;

import com.example.pfe.model.Diagnosis;
import com.example.pfe.model.Diagnosis.Cause;
import com.example.pfe.model.Diagnosis.Confidence;
import com.example.pfe.model.Diagnosis.Evidence;
import com.example.pfe.model.Diagnosis.Risk;
import com.example.pfe.model.Diagnosis.Suggestion;
import com.example.pfe.model.Diagnosis.Verdict;
import com.example.pfe.model.RawDiagnosis;
import com.example.pfe.model.RawDiagnosis.RawCause;
import com.example.pfe.model.RawDiagnosis.RawSuggestion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns untrusted provider output into a safe, grounded {@link Diagnosis}:
 * evidence must point at real log lines, ungrounded causes are dropped,
 * risky commands are flagged, and an empty result becomes INSUFFICIENT_EVIDENCE.
 */
public final class DiagnosisValidator {

    private static final int MAX_EVIDENCE_TEXT = 400;
    private static final int MAX_ITEMS = 8;

    private record RiskRule(Pattern pattern, String note) {}

    private static final List<RiskRule> RISK_RULES = List.of(
            new RiskRule(Pattern.compile("(?i)\\b(curl|wget|iwr|Invoke-WebRequest)\\b.*\\|\\s*(sudo\\s+)?(ba|z)?sh\\b|\\|\\s*iex\\b"), "Runs a remote script without review; never pipe downloads into a shell."),
            new RiskRule(Pattern.compile("--force\\b|\\bpush\\b.*\\s-f\\b"), "Forces past safety checks; can hide conflicts or overwrite work."),
            new RiskRule(Pattern.compile("--legacy-peer-deps"), "Ignores peer dependency requirements; may ship an untested combination."),
            new RiskRule(Pattern.compile("(?i)-DskipTests|-Dmaven\\.test\\.skip|--skip-tests|--no-tests"), "Skips tests; regressions can reach production."),
            new RiskRule(Pattern.compile("--no-verify"), "Bypasses verification hooks."),
            new RiskRule(Pattern.compile("rm\\s+-[a-zA-Z]*r[a-zA-Z]*f|rm\\s+-[a-zA-Z]*f[a-zA-Z]*r|Remove-Item\\b.*-Recurse"), "Deletes files recursively; double-check the path."),
            new RiskRule(Pattern.compile("git\\s+reset\\s+--hard|git\\s+clean\\s+-[a-zA-Z]*f"), "Discards local changes irreversibly."),
            new RiskRule(Pattern.compile("chmod\\s+(-R\\s+)?777"), "Grants everyone full access."),
            new RiskRule(Pattern.compile("(?i)strict-ssl\\s*(=|\\s)\\s*false|NODE_TLS_REJECT_UNAUTHORIZED\\s*=\\s*0|--insecure|-k\\b|sslVerify\\s*=?\\s*false"), "Disables TLS verification; exposes the build to tampering."),
            new RiskRule(Pattern.compile("(?i)\\bsudo\\b"), "Runs with elevated privileges."),
            new RiskRule(Pattern.compile("(?i)\\b(az|kubectl|helm)\\b.*\\b(delete|uninstall)\\b|docker\\s+system\\s+prune|DROP\\s+(TABLE|DATABASE)"), "Destructive operation on shared resources."));

    private DiagnosisValidator() {}

    public static Diagnosis validate(RawDiagnosis raw, List<String> lines, String provider, int redactionCount) {
        List<String> warnings = new ArrayList<>();
        if (redactionCount > 0) {
            warnings.add(redactionCount + " value(s) that looked like secrets were masked before analysis.");
        }

        List<Cause> causes = new ArrayList<>();
        for (RawCause rc : nullSafe(raw.causes())) {
            if (rc == null || isBlank(rc.title())) {
                continue;
            }
            List<Evidence> evidence = groundEvidence(rc.evidenceLines(), lines);
            if (evidence.isEmpty()) {
                warnings.add("Dropped cause \"" + rc.title() + "\" because it did not cite any valid log line.");
                continue;
            }
            causes.add(new Cause(rc.title().trim(), nullToEmpty(rc.explanation()), confidence(rc.confidence()), evidence));
        }

        boolean insufficient = causes.isEmpty() || "INSUFFICIENT_EVIDENCE".equalsIgnoreCase(raw.verdict());
        if (insufficient) {
            causes.replaceAll(c -> new Cause(c.title(), c.explanation(), Confidence.LOW, c.evidence()));
        }
        List<String> missing = cleanStrings(raw.missingInformation());
        if (insufficient && missing.isEmpty()) {
            missing = List.of("The complete output of the failing step, including the lines before the first ##[error].");
        }

        String summary = isBlank(raw.summary())
                ? (insufficient ? "Not enough evidence in the log to diagnose this failure." : "Diagnosis available.")
                : raw.summary().trim();

        return new Diagnosis(
                insufficient ? Verdict.INSUFFICIENT_EVIDENCE : Verdict.DIAGNOSED,
                summary,
                isBlank(raw.failedStep()) ? null : raw.failedStep().trim(),
                isBlank(raw.category()) ? "unknown" : raw.category().trim().toLowerCase(Locale.ROOT),
                List.copyOf(causes),
                suggestions(raw.checks()),
                suggestions(raw.fixes()),
                missing,
                List.copyOf(warnings),
                provider,
                lines.size(),
                redactionCount);
    }

    private static List<Evidence> groundEvidence(List<Integer> lineNumbers, List<String> lines) {
        Set<Integer> unique = new LinkedHashSet<>();
        for (Integer n : nullSafe(lineNumbers)) {
            if (n != null && n >= 1 && n <= lines.size() && !lines.get(n - 1).isBlank()) {
                unique.add(n);
            }
        }
        return unique.stream()
                .sorted()
                .limit(MAX_ITEMS)
                .map(n -> new Evidence(n, truncate(lines.get(n - 1))))
                .toList();
    }

    private static List<Suggestion> suggestions(List<RawSuggestion> raw) {
        return nullSafe(raw).stream()
                .filter(Objects::nonNull)
                .filter(s -> !isBlank(s.action()))
                .limit(MAX_ITEMS)
                .map(s -> {
                    String command = isBlank(s.command()) ? null : s.command().trim();
                    String note = riskNote(command);
                    return new Suggestion(s.action().trim(), command, nullToEmpty(s.rationale()),
                            note == null ? Risk.SAFE : Risk.CAUTION, note);
                })
                .toList();
    }

    static String riskNote(String command) {
        if (command == null) {
            return null;
        }
        for (RiskRule rule : RISK_RULES) {
            if (rule.pattern().matcher(command).find()) {
                return rule.note();
            }
        }
        return null;
    }

    private static Confidence confidence(String value) {
        try {
            return value == null ? Confidence.LOW : Confidence.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Confidence.LOW;
        }
    }

    private static List<String> cleanStrings(List<String> values) {
        return nullSafe(values).stream().filter(v -> !isBlank(v)).map(String::trim).distinct().limit(MAX_ITEMS).toList();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s) {
        return s.length() > MAX_EVIDENCE_TEXT ? s.substring(0, MAX_EVIDENCE_TEXT) + "..." : s;
    }
}
