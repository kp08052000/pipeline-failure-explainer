# AI Pipeline Failure Explainer

Paste a **sanitised** Azure DevOps pipeline log and get a structured diagnosis:

- a short **summary** of the failure
- **likely causes**, each linked to the log lines that support it
- **suggested checks** (read-only, run first) and **suggested fixes**
- **missing information** needed to diagnose it, with an explicit **"Insufficient evidence"** verdict when the log does not show the real error

Example: an `npm install` fails with `ERESOLVE` → the tool identifies the peer dependency conflict, highlights the `Found:` / `peer ... from` lines, and proposes checks (`npm explain`, lockfile diff, Node/npm versions) before fixes. `--force` / `--legacy-peer-deps` are only mentioned as a flagged last resort.

## Scope (MVP)

- Pasted logs only. No Azure authentication or live pipeline integration.
- Suggested commands are display-only text. **Nothing is ever executed.**

## Structure

```
backend/   Java 17 + Spring Boot 4 REST API (calls an OpenAI-compatible API)
frontend/  Angular 22 UI (log input, sample failures, diagnosis cards, evidence view)
```

### How a request flows

```mermaid
flowchart LR
  UI[Angular UI] -->|POST /api/diagnose| C[DiagnosisController]
  C --> R[LogRedactor<br/>mask obvious secrets]
  R --> N[Number lines L1..Ln]
  N --> P{Provider}
  P -->|AI_API_KEY set| AI[OpenAI-compatible API<br/>strict JSON schema]
  P -->|no key| OR[Offline demo rules]
  AI --> V[DiagnosisValidator]
  OR --> V
  V -->|grounded Diagnosis| UI
```

`DiagnosisValidator` treats model output as untrusted:

- Evidence text is taken from the real log line, never from the model.
- Causes that cite no valid line are dropped (and reported as a warning).
- No grounded causes → verdict becomes `INSUFFICIENT_EVIDENCE` with missing-information prompts.
- Risky commands (`--force`, `--legacy-peer-deps`, `-DskipTests`, `rm -rf`, `git reset --hard`, TLS disabling, destructive `az`/`kubectl` calls, ...) are flagged **CAUTION** with a note.

The system prompt ([backend/src/main/resources/ai/system-prompt.txt](backend/src/main/resources/ai/system-prompt.txt)) also tells the model to treat the log as untrusted data (prompt-injection defence), prefer checks over shortcuts, and say when evidence is insufficient.

## Run locally

Prerequisites: JDK 17+, Maven 3.8+, Node 20.19+/22/24.

### Backend (port 8080, bound to 127.0.0.1)

```powershell
cd backend
# Optional - without a key (or a local preset) the app uses offline demo rules
$env:AI_PRESET = "gemini"            # see presets below
$env:GEMINI_API_KEY = "<your key>"   # or AI_API_KEY
mvn spring-boot:run
```

#### AI presets (`AI_PRESET`)

All use the OpenAI-compatible Chat Completions API. `AI_BASE_URL` / `AI_MODEL` override the preset; default models are starting points and may need updating.

| Preset | Endpoint | Default model | Key variable |
|---|---|---|---|
| `openai` (default) | api.openai.com | `gpt-4.1-mini` | `OPENAI_API_KEY` |
| `ollama` | localhost:11434 (local) | `qwen2.5:7b` | none |
| `lmstudio` | localhost:1234 (local) | set `AI_MODEL` | none |
| `github` | models.github.ai | `openai/gpt-4.1-mini` | `GITHUB_TOKEN` |
| `gemini` | generativelanguage.googleapis.com | `gemini-flash-latest` | `GEMINI_API_KEY` |
| `groq` | api.groq.com | `llama-3.3-70b-versatile` | `GROQ_API_KEY` |
| `openrouter` | openrouter.ai | set `AI_MODEL` | `OPENROUTER_API_KEY` |
| `mistral` | api.mistral.ai | `mistral-small-latest` | `MISTRAL_API_KEY` |

Azure OpenAI: `AI_BASE_URL=https://<resource>.openai.azure.com/openai/v1`, `AI_API_KEY_HEADER=api-key`, `AI_MODEL=<deployment>`, `AI_API_KEY=<key>`.

Free cloud tiers may retain or train on prompts - use only sanitised logs. Local presets keep logs on your machine.

**Behind a corporate proxy:** Java ignores `HTTPS_PROXY`, and TLS-inspecting proxies use a company root CA the JDK does not trust. Pass the proxy and use the Windows certificate store:

```powershell
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dhttps.proxyHost=<proxy-host> -Dhttps.proxyPort=<port> -Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

If a model returns HTTP 503 ("high demand") or 404 ("no longer available"), set `AI_MODEL` to another model your key lists, e.g. `gemini-flash-lite-latest`.

| Variable | Default | Notes |
|---|---|---|
| `AI_PROVIDER` | `auto` | `auto`, `openai` (force AI) or `offline` |
| `AI_PRESET` | `openai` | See table above |
| `AI_API_KEY` | _(empty)_ | Overrides the preset's key variable |
| `AI_BASE_URL` / `AI_MODEL` | _(preset)_ | Override the preset |
| `AI_API_KEY_HEADER` | `Authorization` | Use `api-key` for Azure OpenAI key auth |
| `AI_RESPONSE_FORMAT` | `auto` | `json_schema` (strict), `json_object`, or `auto` = strict with fallback to JSON mode if the model rejects schemas |
| `AI_TIMEOUT` | `60s` | Use `180s` for local CPU models |

### Frontend (port 4200, proxies `/api` to the backend)

```powershell
cd frontend
npm install
npm start
```

Open http://localhost:4200, pick a sample (npm conflict, Maven test failure, Docker push unauthorized, vague failure) or paste a log, and choose **Explain failure**. Click an evidence line to jump to it in the **Evidence** tab.

## Deploy (personal demo)

The [Dockerfile](Dockerfile) builds the Angular UI, embeds it in the Spring Boot jar and runs one container: UI and API share one origin, so no CORS or dev proxy is needed. The container listens on `0.0.0.0:$PORT` (default 8080). Tests run during the image build.

Runtime settings (set in the hosting platform, never in code):

| Variable | Example | Notes |
|---|---|---|
| `AI_PRESET` | `gemini` | |
| `AI_MODEL` | `gemini-flash-lite-latest` | |
| `GEMINI_API_KEY` | _(secret)_ | Store as a platform secret. Use a dedicated key for the deployment |
| `RATE_LIMIT_PER_MINUTE` | `5` | Per client IP, on `POST /api/diagnose` |
| `RATE_LIMIT_PER_DAY` | `200` | Global daily cap - protects your free quota |

The rate limiter is in-memory, so run a **single instance**. There is no login: anyone with the URL can use it up to these limits, and their logs go to the AI provider (the UI says so).

**Google Cloud Run** (scales to zero, free tier):

1. Create a secret `gemini-api-key` in Secret Manager and grant the Cloud Run service account the *Secret Manager Secret Accessor* role.
2. From the repo root:

```bash
gcloud run deploy pipeline-explainer --source . --region europe-west1 \
  --allow-unauthenticated --max-instances 1 \
  --set-env-vars AI_PRESET=gemini,AI_MODEL=gemini-flash-lite-latest \
  --set-secrets GEMINI_API_KEY=gemini-api-key:latest
```

**Render** (free tier, sleeps when idle): push the repo to GitHub, create a *Web Service* from it, choose *Docker*, add the variables above (mark `GEMINI_API_KEY` as secret) and deploy.

**Any Docker host:**

```bash
docker build -t pipeline-explainer .
docker run -p 8080:8080 -e AI_PRESET=gemini -e AI_MODEL=gemini-flash-lite-latest -e GEMINI_API_KEY pipeline-explainer
```

(`-e GEMINI_API_KEY` without a value passes it from your shell instead of the command line.) The corporate proxy settings above are only for running inside a company network.

## API

`POST /api/diagnose` with `{ "log": "..." }` (max 100,000 chars) returns:

```json
{
  "verdict": "DIAGNOSED | INSUFFICIENT_EVIDENCE",
  "summary": "...",
  "failedStep": "npm ci",
  "category": "dependency",
  "causes": [{ "title": "...", "explanation": "...", "confidence": "HIGH", "evidence": [{ "line": 10, "text": "npm error code ERESOLVE" }] }],
  "checks": [{ "action": "...", "command": "npm explain <package-name>", "rationale": "...", "risk": "SAFE", "riskNote": null }],
  "fixes": [{ "action": "...", "command": "npm install --legacy-peer-deps", "rationale": "...", "risk": "CAUTION", "riskNote": "..." }],
  "missingInformation": ["..."],
  "warnings": ["..."],
  "provider": "openai:gpt-4.1-mini",
  "lineCount": 27,
  "redactionCount": 0
}
```

`GET /api/status` returns the active provider.

## Tests

```powershell
cd backend;  mvn test              # validator, redactor, offline provider
cd frontend; npx ng test --watch=false
```

## Security notes

- Paste sanitised logs. The backend additionally masks obvious secrets (bearer tokens, JWTs, `password=`, storage keys, URL credentials, GitHub/AWS keys) before the log reaches the AI API — this is best-effort, not a guarantee.
- The API key is read from the environment only; it is never sent to the browser.
- Locally the backend binds to `127.0.0.1`; the container binds to `0.0.0.0`. There is no authentication - public deployments rely on the rate limit and daily cap, so keep them low and use a dedicated, revocable API key.
