package com.example.pfe.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort masking of obvious secrets before a log leaves the server. Not a substitute for sanitising. */
public final class LogRedactor {

    public record Result(String text, int count) {}

    private static final String MASK = "[REDACTED]";

    private record Rule(Pattern pattern, String replacement) {}

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{8,}"), "$1 " + MASK),
            new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}"), MASK),
            new Rule(Pattern.compile("(?i)(AccountKey|SharedAccessKey|sig)=([^;&\\s\"']+)"), "$1=" + MASK),
            new Rule(Pattern.compile("(?i)(://)[^/\\s:@]+:[^/\\s@]+@"), "$1" + MASK + "@"),
            new Rule(Pattern.compile("(?i)\\b(password|passwd|pwd|secret|client_secret|token|api[_-]?key|access[_-]?key|_authToken|_auth)(\\s*[=:]\\s*)(\"[^\"\\n]*\"|'[^'\\n]*'|[^\\s,;]+)"),
                    "$1$2" + MASK),
            new Rule(Pattern.compile("\\b(ghp|gho|ghs|github_pat)_[A-Za-z0-9_]{20,}"), MASK),
            new Rule(Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"), MASK));

    private LogRedactor() {}

    public static Result redact(String input) {
        String text = input;
        int count = 0;
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                count++;
                m.appendReplacement(sb, rule.replacement());
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return new Result(text, count);
    }
}
