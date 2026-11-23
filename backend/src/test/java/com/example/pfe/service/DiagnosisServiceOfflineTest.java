package com.example.pfe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pfe.ai.OfflineRulesProvider;
import com.example.pfe.model.Diagnosis;
import com.example.pfe.model.Diagnosis.Verdict;
import org.junit.jupiter.api.Test;

class DiagnosisServiceOfflineTest {

    private final DiagnosisService service = new DiagnosisService(new OfflineRulesProvider());

    @Test
    void npmConflictIsDiagnosedWithEvidenceAndForceIsOnlyACaution() {
        String log = """
                ##[section]Starting: npm install
                npm error code ERESOLVE
                npm error ERESOLVE unable to resolve dependency tree
                npm error Found: @angular/core@17.3.12
                npm error Could not resolve dependency:
                npm error peer @angular/core@"^18.0.0" from @angular/cdk@18.2.0
                ##[error]Bash exited with code '1'.""";

        Diagnosis d = service.diagnose(log);

        assertThat(d.verdict()).isEqualTo(Verdict.DIAGNOSED);
        assertThat(d.category()).isEqualTo("dependency");
        assertThat(d.failedStep()).isEqualTo("npm install");
        assertThat(d.causes().get(0).evidence()).extracting(Diagnosis.Evidence::line).containsExactly(2, 4, 6);
        assertThat(d.fixes().get(0).risk()).isEqualTo(Diagnosis.Risk.SAFE);
        assertThat(d.fixes()).anyMatch(f -> f.risk() == Diagnosis.Risk.CAUTION);
    }

    @Test
    void genericExitCodeIsInsufficientEvidence() {
        Diagnosis d = service.diagnose("##[section]Starting: Build\n##[error]Bash exited with code '1'.\n");

        assertThat(d.verdict()).isEqualTo(Verdict.INSUFFICIENT_EVIDENCE);
        assertThat(d.causes()).isEmpty();
        assertThat(d.missingInformation()).isNotEmpty();
    }
}
