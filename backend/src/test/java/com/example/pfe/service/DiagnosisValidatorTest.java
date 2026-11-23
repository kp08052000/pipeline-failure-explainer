package com.example.pfe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pfe.model.Diagnosis;
import com.example.pfe.model.Diagnosis.Risk;
import com.example.pfe.model.Diagnosis.Verdict;
import com.example.pfe.model.RawDiagnosis;
import com.example.pfe.model.RawDiagnosis.RawCause;
import com.example.pfe.model.RawDiagnosis.RawSuggestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosisValidatorTest {

    private static final List<String> LINES = List.of(
            "##[section]Starting: npm install",
            "npm error code ERESOLVE",
            "npm error ERESOLVE unable to resolve dependency tree",
            "",
            "##[error]Bash exited with code '1'.");

    @Test
    void evidenceTextComesFromTheLogNotTheModel() {
        RawDiagnosis raw = new RawDiagnosis("DIAGNOSED", "npm conflict", "npm install", "dependency",
                List.of(new RawCause("Peer conflict", "x", "high", List.of(3, 2, 2))), List.of(), List.of(), List.of());

        Diagnosis d = DiagnosisValidator.validate(raw, LINES, "test", 0);

        assertThat(d.verdict()).isEqualTo(Verdict.DIAGNOSED);
        assertThat(d.causes()).singleElement().satisfies(c -> {
            assertThat(c.confidence()).isEqualTo(Diagnosis.Confidence.HIGH);
            assertThat(c.evidence()).extracting(Diagnosis.Evidence::line).containsExactly(2, 3);
            assertThat(c.evidence().get(0).text()).isEqualTo("npm error code ERESOLVE");
        });
    }

    @Test
    void causesWithoutValidLinesAreDroppedAndBecomeInsufficientEvidence() {
        RawDiagnosis raw = new RawDiagnosis("DIAGNOSED", "guess", null, "dependency",
                List.of(new RawCause("Invented cause", "x", "HIGH", List.of(0, 4, 99))), List.of(), List.of(), null);

        Diagnosis d = DiagnosisValidator.validate(raw, LINES, "test", 0);

        assertThat(d.verdict()).isEqualTo(Verdict.INSUFFICIENT_EVIDENCE);
        assertThat(d.causes()).isEmpty();
        assertThat(d.missingInformation()).isNotEmpty();
        assertThat(d.warnings()).anyMatch(w -> w.contains("Invented cause"));
    }

    @Test
    void riskyCommandsAreFlagged() {
        RawDiagnosis raw = new RawDiagnosis("DIAGNOSED", "s", null, "dependency",
                List.of(new RawCause("c", "x", "LOW", List.of(2))),
                List.of(new RawSuggestion("Explain", "npm explain react", "r")),
                List.of(new RawSuggestion("Force", "npm install --force", "r"),
                        new RawSuggestion("Legacy", "npm install --legacy-peer-deps", "r"),
                        new RawSuggestion("Skip", "mvn package -DskipTests", "r")),
                List.of());

        Diagnosis d = DiagnosisValidator.validate(raw, LINES, "test", 0);

        assertThat(d.checks()).extracting(Diagnosis.Suggestion::risk).containsExactly(Risk.SAFE);
        assertThat(d.fixes()).extracting(Diagnosis.Suggestion::risk).containsOnly(Risk.CAUTION);
    }
}
