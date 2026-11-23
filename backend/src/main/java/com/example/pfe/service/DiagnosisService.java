package com.example.pfe.service;

import com.example.pfe.ai.DiagnosisProvider;
import com.example.pfe.model.Diagnosis;
import com.example.pfe.model.RawDiagnosis;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisService {

    private static final int MAX_LINE_LENGTH_FOR_AI = 1_000;

    private final DiagnosisProvider provider;

    public DiagnosisService(DiagnosisProvider provider) {
        this.provider = provider;
    }

    public String providerName() {
        return provider.name();
    }

    public Diagnosis diagnose(String log) {
        LogRedactor.Result redacted = LogRedactor.redact(log.replace("\r\n", "\n").replace('\r', '\n'));
        List<String> lines = List.of(redacted.text().split("\n", -1));

        StringBuilder numbered = new StringBuilder(redacted.text().length() + lines.size() * 8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > MAX_LINE_LENGTH_FOR_AI) {
                line = line.substring(0, MAX_LINE_LENGTH_FOR_AI) + " ...[truncated]";
            }
            numbered.append('L').append(i + 1).append("| ").append(line).append('\n');
        }

        RawDiagnosis raw = provider.diagnose(numbered.toString(), lines);
        return DiagnosisValidator.validate(raw, lines, provider.name(), redacted.count());
    }
}
