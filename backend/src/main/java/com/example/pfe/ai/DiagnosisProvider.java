package com.example.pfe.ai;

import com.example.pfe.model.RawDiagnosis;
import java.util.List;

public interface DiagnosisProvider {

    /** Short identifier shown in the UI, e.g. "openai:gpt-4.1-mini" or "offline-rules". */
    String name();

    /**
     * @param numberedLog redacted log with each line prefixed "L&lt;n&gt;| "
     * @param lines       the same redacted lines, index 0 = line 1
     */
    RawDiagnosis diagnose(String numberedLog, List<String> lines);
}
