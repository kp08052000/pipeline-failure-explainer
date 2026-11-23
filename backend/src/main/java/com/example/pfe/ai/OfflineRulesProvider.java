package com.example.pfe.ai;

import com.example.pfe.model.RawDiagnosis;
import com.example.pfe.model.RawDiagnosis.RawCause;
import com.example.pfe.model.RawDiagnosis.RawSuggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic fallback used when no AI API key is configured, so the UI can be demoed offline.
 * Covers a few common failure signatures only; anything else is reported as insufficient evidence.
 */
public class OfflineRulesProvider implements DiagnosisProvider {

    private static final int MAX_EVIDENCE = 4;

    private record Rule(String category, String title, String explanation, String confidence, Pattern pattern,
                        List<RawSuggestion> checks, List<RawSuggestion> fixes, List<String> missing) {}

    private static RawSuggestion s(String action, String command, String rationale) {
        return new RawSuggestion(action, command, rationale);
    }

    private static final List<Rule> RULES = List.of(
            new Rule("dependency", "npm peer dependency conflict (ERESOLVE)",
                    "npm could not build a dependency tree because a package requires a peer version that conflicts with the version already resolved.",
                    "HIGH",
                    Pattern.compile("code ERESOLVE|Conflicting peer dependency|peer .+ from .+@|npm (ERR!|error) Found: "),
                    List.of(
                            s("Read the 'Found' and 'peer ... from' lines to identify both sides of the conflict", null,
                                    "The conflict is between the installed version and the peer range one package demands."),
                            s("Ask npm why the conflicting package is in the tree", "npm explain <package-name>",
                                    "Shows which direct dependency pulls in each version."),
                            s("Compare dependency manifests with the last green run", "git diff <last-green-commit> -- package.json package-lock.json",
                                    "A recent upgrade or a missing lockfile change is the usual trigger."),
                            s("Confirm the agent's Node/npm versions match local development", "node -v && npm -v",
                                    "npm 7+ enforces peer dependencies; older npm only warned.")),
                    List.of(
                            s("Align versions so the peer range is satisfied (upgrade the dependent package or pin a compatible version)", null,
                                    "Fixes the actual incompatibility instead of hiding it."),
                            s("Regenerate and commit package-lock.json locally, then use npm ci in the pipeline", "npm ci",
                                    "Gives reproducible installs that match what was tested locally."),
                            s("Last resort only: install with --legacy-peer-deps while a proper fix is tracked", "npm install --legacy-peer-deps",
                                    "Ignores peer requirements; can ship an untested combination.")),
                    List.of("package.json dependencies and devDependencies", "Whether package-lock.json is committed and used (npm ci vs npm install)", "Node/npm version on the build agent")),
            new Rule("authentication", "Registry or feed authentication failure",
                    "The package/container registry rejected the credentials used by the pipeline.",
                    "MEDIUM",
                    Pattern.compile("(?i)E401|E403|unauthorized: authentication required|denied: requested access to the resource is denied|401 Unauthorized|403 Forbidden"),
                    List.of(
                            s("Check the service connection or feed permissions used by this pipeline", null,
                                    "Build service identity often lacks Contributor/Reader rights on the feed or registry."),
                            s("Check that the authenticate task (npmAuthenticate / Docker@2 login) runs before the failing step", null,
                                    "Task order and the correct .npmrc / registry URL matter."),
                            s("Confirm whether the credential or PAT has expired", null, "Expired secrets cause sudden 401s on unchanged pipelines.")),
                    List.of(s("Renew the credential or grant the pipeline identity access, then re-run", null, "Restores access without weakening security.")),
                    List.of("Registry/feed URL being used", "Pipeline YAML for the authentication step", "Whether the failure started after a permission or secret change")),
            new Rule("dependency", "Maven dependency resolution failure",
                    "Maven could not download or resolve one or more artifacts.",
                    "HIGH",
                    Pattern.compile("Could not resolve dependencies|Failed to collect dependencies|Could not transfer artifact|Could not find artifact"),
                    List.of(
                            s("Identify the exact artifact and repository from the error line", null, "Tells you whether it is a missing version, a typo or a network/auth issue."),
                            s("Check the artifact exists in the configured repository/feed", "mvn -U dependency:tree", "-U forces a re-check of missing/cached failures locally."),
                            s("Check settings.xml/mirror configuration used by the agent", null, "Agents may use a different mirror than developers.")),
                    List.of(s("Correct the version or repository configuration so the artifact is resolvable", null, "Addresses the root cause.")),
                    List.of("pom.xml dependency section for the failing artifact", "settings.xml / Maven task configuration")),
            new Rule("test", "Unit test failures",
                    "The build compiled but one or more tests failed, so the test plugin stopped the build.",
                    "HIGH",
                    Pattern.compile("There are test failures|Tests run: \\d+, Failures: [1-9]|Tests run: \\d+, Failures: \\d+, Errors: [1-9]|\\[ERROR\\] Failures:|\\[ERROR\\]\\s+\\S+Test\\.\\w+|expected:? ?<.*> but was:? ?<|AssertionError|\\d+ failing"),
                    List.of(
                            s("Open the failing test(s) named in the log and read the assertion message", null, "The assertion shows the expected vs actual behaviour."),
                            s("Run the single failing test locally", "mvn -Dtest=<TestClass>#<method> test", "Confirms whether it is a real regression or environment-specific."),
                            s("Check for time zone, locale, ordering or shared-state assumptions", null, "Common causes of tests that pass locally but fail on agents.")),
                    List.of(
                            s("Fix the code or the test expectation once the intended behaviour is confirmed", null, "Keeps the test meaningful."),
                            s("Do not skip tests to unblock the pipeline", null, "Skipping hides regressions; quarantine flaky tests explicitly instead.")),
                    List.of("Full surefire/test report for the failing test", "Recent commits touching the tested code")),
            new Rule("build", "Compilation error",
                    "The source code does not compile on the build agent.",
                    "HIGH",
                    Pattern.compile("COMPILATION ERROR|cannot find symbol|(?i:error\\]?\\s*TS\\d+)|error CS\\d+|SyntaxError:"),
                    List.of(
                            s("Open the file and line reported by the compiler", null, "Compiler errors point to the exact location."),
                            s("Check that all changed files were committed", "git status", "A missing file or unpushed change compiles locally but not in CI."),
                            s("Compare compiler/SDK versions between agent and local", null, "Different language versions can reject valid local code.")),
                    List.of(s("Fix the reported compile error and push", null, "Direct fix.")),
                    List.of("Compiler/SDK version on the agent")),
            new Rule("infrastructure", "Build agent out of disk space",
                    "The agent ran out of disk space while writing files.",
                    "HIGH",
                    Pattern.compile("No space left on device|ENOSPC|There is not enough space on the disk"),
                    List.of(s("Check agent disk usage and workspace size", "df -h", "Confirms the agent is full and which volume.")),
                    List.of(s("Clean the workspace in the job (workspace: clean: all) or use a larger agent", null, "Prefer pipeline-level cleanup over manual deletion on shared agents.")),
                    List.of("Agent type (Microsoft-hosted or self-hosted)")));

    @Override
    public String name() {
        return "offline-rules";
    }

    @Override
    public RawDiagnosis diagnose(String numberedLog, List<String> lines) {
        List<RawCause> causes = new ArrayList<>();
        List<RawSuggestion> checks = new ArrayList<>();
        List<RawSuggestion> fixes = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        String category = "unknown";

        for (Rule rule : RULES) {
            List<Integer> hits = new ArrayList<>();
            for (int i = 0; i < lines.size() && hits.size() < MAX_EVIDENCE; i++) {
                if (rule.pattern().matcher(lines.get(i)).find()) {
                    hits.add(i + 1);
                }
            }
            if (!hits.isEmpty()) {
                if (causes.isEmpty()) {
                    category = rule.category();
                }
                causes.add(new RawCause(rule.title(), rule.explanation(), rule.confidence(), hits));
                checks.addAll(rule.checks());
                fixes.addAll(rule.fixes());
                missing.addAll(rule.missing());
            }
        }

        if (causes.isEmpty()) {
            return new RawDiagnosis("INSUFFICIENT_EVIDENCE",
                    "The log shows the step failed but does not contain a recognisable underlying error.",
                    failedStep(lines), "unknown", List.of(),
                    List.of(s("Enable system diagnostics and re-run the failing job", null,
                            "Setting the pipeline variable system.debug=true produces verbose task output.")),
                    List.of(),
                    List.of("The complete output of the failing step, including lines before the first ##[error]",
                            "The pipeline YAML for the failing task",
                            "Whether this step passed on the previous run and what changed"));
        }

        return new RawDiagnosis("DIAGNOSED", causes.get(0).title() + " detected in the log.", failedStep(lines), category,
                causes, checks, fixes, missing);
    }

    /** Last "Starting: <task>" section marker before the first ##[error], if any. */
    private static String failedStep(List<String> lines) {
        String step = null;
        for (String line : lines) {
            int idx = line.indexOf("##[section]Starting: ");
            if (idx >= 0) {
                step = line.substring(idx + "##[section]Starting: ".length()).trim();
            }
            if (line.contains("##[error]")) {
                break;
            }
        }
        return step;
    }
}
