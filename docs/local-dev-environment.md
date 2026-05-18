# Local Development Environment

## Design Principle

The Quarkus applications (chat UI + 3 MCP servers) remain **identical** between local dev and OpenShift production. All environment differences live in two places:

- **OGX config file** -- different per environment (local Ollama vs remote vLLM)
- **One env var** on the Quarkus chat UI -- `LLAMA_STACK_URL`

This makes the "local to prod" webinar story trivially demonstrable.

## What Changes Between Environments

```mermaid
graph LR
    subgraph localDev [Local Dev]
        OllamaLocal["Ollama\nlocalhost:11434"]
        OGXLocal["OGX Server\nlocalhost:8321"]
        MCP1L["MCP Patient Records\nlocalhost:8081"]
        MCP2L["MCP Medical Knowledge\nlocalhost:8082"]
        MCP3L["MCP Scheduling\nlocalhost:8083"]
        AppL["Quarkus Chat UI\nlocalhost:8080"]

        AppL -->|"LLAMA_STACK_URL\n=localhost:8321"| OGXLocal
        OGXLocal -->|"remote::ollama"| OllamaLocal
        OGXLocal --> MCP1L
        OGXLocal --> MCP2L
        OGXLocal --> MCP3L
    end

    subgraph prod [OpenShift Production]
        vLLM1["vLLM Granite 8B"]
        vLLM2["vLLM Qwen 32B"]
        OGXProd["Llama Stack OGX\ntriage-llamastack:8321"]
        MCP1P["mcp-patient-records:8080"]
        MCP2P["mcp-medical-knowledge:8080"]
        MCP3P["mcp-scheduling:8080"]
        AppP["Quarkus Chat UI"]

        AppP -->|"LLAMA_STACK_URL\n=triage-llamastack:8321"| OGXProd
        OGXProd -->|"remote::openai"| vLLM1
        OGXProd -->|"remote::openai"| vLLM2
        OGXProd --> MCP1P
        OGXProd --> MCP2P
        OGXProd --> MCP3P
    end
```

| Component | Local Dev | OpenShift Prod | What Changes |
|---|---|---|---|
| Quarkus Chat UI code | identical | identical | nothing |
| Quarkus MCP server code | identical | identical | nothing |
| `LLAMA_STACK_URL` | `http://localhost:8321` | `http://triage-llamastack:8321` | env var only |
| `MODEL_NAME` | `triage-model` | `triage-model` | nothing (logical alias) |
| OGX inference provider | `remote::ollama` -> localhost:11434 | `remote::openai` -> vLLM svc URLs | OGX config file |
| OGX MCP connector URLs | `localhost:8081-8083` | k8s service names | OGX config file |
| OGX model mapping | `triage-model` -> Ollama model | `triage-model` -> Granite/Qwen | OGX config file |

## Prerequisites

- **Ollama** installed and running (`ollama serve`)
- **uv** installed (`brew install uv` or see https://docs.astral.sh/uv/)
- **Quarkus CLI** installed (`quarkus --version`)
- **Java 21+**

## Step 1: Set Up OGX in a Local Virtual Environment

Create a Python virtual environment inside the `ogx_configs/` directory using `uv`, keeping OGX isolated from the system Python:

```bash
cd ogx_configs/
uv venv .venv --python 3.12
uv pip install ogx --python .venv/bin/python
```

The `.venv/` directory is gitignored. Run OGX via the venv-local binary:

```bash
ogx_configs/.venv/bin/ogx stack run ogx_configs/local-dev.yaml
```

Or activate the venv first:

```bash
source ogx_configs/.venv/bin/activate
ogx stack run ogx_configs/local-dev.yaml
deactivate
```

This keeps OGX completely isolated -- no global Python packages polluted. The `ogx_configs/` directory is the self-contained home for everything OGX: the venv, the local dev config, and the production reference config.

## Step 2: Pull an Ollama Model with Tool Calling Support

For closest parity with the production Granite model:

```bash
ollama pull granite3.3:8b
```

The `granite3.3:8b` model from IBM supports function calling and is the closest local equivalent to the production `Granite 3.3 8B Instruct` on vLLM.

Alternatively, `qwen3:14b` has excellent tool calling support and can be used instead by setting the `OLLAMA_MODEL` environment variable before starting OGX:

```bash
export OLLAMA_MODEL=qwen3:14b
```

Either model works -- OGX maps the logical alias `triage-model` to whichever you configure.

## Step 3: OGX Configuration Files

Two OGX config files live in `ogx_configs/`:

### `ogx_configs/local-dev.yaml` -- Local Development

Uses Ollama as the inference provider with MCP servers running on localhost:

```yaml
version: 2
distro_name: local-dev

apis:
  - responses
  - inference

providers:
  inference:
    - provider_id: local-ollama
      provider_type: remote::ollama
      config:
        url: ${env.OLLAMA_URL:=http://localhost:11434}
  responses:
    - provider_id: builtin
      provider_type: inline::builtin
      config: {}

models:
  - model_id: triage-model
    provider_id: local-ollama
    provider_model_id: ${env.OLLAMA_MODEL:=granite3.3:8b}

connectors:
  - connector_id: "mcp::patient-records"
    connector_type: mcp
    url: "http://localhost:8081/mcp"
  - connector_id: "mcp::medical-knowledge"
    connector_type: mcp
    url: "http://localhost:8082/mcp"
  - connector_id: "mcp::scheduling"
    connector_type: mcp
    url: "http://localhost:8083/mcp"

server:
  port: 8321
```

### `ogx_configs/openshift-prod.yaml` -- OpenShift Production (reference)

Uses vLLM instances as inference providers with MCP servers as k8s services:

```yaml
version: 2
distro_name: openshift-prod

apis:
  - responses
  - inference

providers:
  inference:
    - provider_id: granite-fast
      provider_type: remote::openai
      config:
        url: https://granite-8b.openshift-ai.svc:8080/v1
    - provider_id: qwen-powerful
      provider_type: remote::openai
      config:
        url: https://qwen-32b.openshift-ai.svc:8080/v1
  responses:
    - provider_id: builtin
      provider_type: inline::builtin
      config: {}

models:
  - model_id: triage-model
    provider_id: granite-fast
    provider_model_id: granite-3.3-8b-instruct
  - model_id: granite-3.3-8b
    provider_id: granite-fast
    provider_model_id: granite-3.3-8b-instruct
  - model_id: qwen-2.5-32b
    provider_id: qwen-powerful
    provider_model_id: qwen-2.5-32b-instruct

connectors:
  - connector_id: "mcp::patient-records"
    connector_type: mcp
    url: "http://mcp-patient-records:8080/mcp"
  - connector_id: "mcp::medical-knowledge"
    connector_type: mcp
    url: "http://mcp-medical-knowledge:8080/mcp"
  - connector_id: "mcp::scheduling"
    connector_type: mcp
    url: "http://mcp-scheduling:8080/mcp"

server:
  port: 8321
```

The diff between the two configs is the **entire "local to prod" story**: swap `remote::ollama` for `remote::openai`, change URLs from localhost to k8s service names. The Quarkus apps are untouched.

## Step 4: Quarkus App Configuration

### Chat UI (`triage-assistant/src/main/resources/application.properties`)

```properties
# Llama Stack connection -- only this URL changes between environments
quarkus.rest-client.llamastack.url=${LLAMA_STACK_URL:http://localhost:8321}

# Model name -- stays the same everywhere (logical alias resolved by OGX)
triage.model=${MODEL_NAME:triage-model}

# System prompt
triage.system-prompt=You are a clinical triage assistant for hospital nurses. \
  You help nurses during patient intake by looking up patient records, \
  assessing symptom urgency, checking drug interactions, and scheduling \
  specialist appointments. \
  IMPORTANT: You ASSIST the nurse. You do NOT make clinical decisions. \
  Always state the ESI triage level when assessing symptoms. \
  Flag red flags and recommended immediate actions clearly.
```

### MCP Servers (`mcp-*/src/main/resources/application.properties`)

```properties
# Each MCP server just needs its port configured
quarkus.http.port=${HTTP_PORT:8081}   # 8082 for medical-knowledge, 8083 for scheduling
```

No AI-related config in MCP servers -- they are plain Quarkus apps with `@Tool` annotations.

## Running Locally

### Option A: Separate Terminals (recommended for development)

```
Terminal 1: cd mcp-patient-records && quarkus dev -Dquarkus.http.port=8081
Terminal 2: cd mcp-medical-knowledge && quarkus dev -Dquarkus.http.port=8082
Terminal 3: cd mcp-scheduling && quarkus dev -Dquarkus.http.port=8083
Terminal 4: ollama serve  (if not already running)
Terminal 5: ogx_configs/.venv/bin/ogx stack run ogx_configs/local-dev.yaml
Terminal 6: cd triage-assistant && quarkus dev
```

Open `http://localhost:8080` and interact with the triage assistant.

### Option B: Startup Script

Run `./dev-start.sh` from the project root. It starts all MCP servers, the OGX server, and the Quarkus chat UI in background processes.

## Port Assignments

| Service | Port |
|---------|------|
| triage-assistant (Quarkus Chat UI) | 8080 |
| mcp-patient-records | 8081 |
| mcp-medical-knowledge | 8082 |
| mcp-scheduling | 8083 |
| OGX server | 8321 |
| Ollama | 11434 |

## Local to Production Transition

Moving from local dev to OpenShift requires **zero code changes**:

1. **Quarkus apps**: Set `LLAMA_STACK_URL` env var in the OpenShift Deployment to point at the Llama Stack service instead of localhost
2. **OGX config**: The `LlamaStackDistribution` CR on OpenShift uses the equivalent of `ogx_configs/openshift-prod.yaml` -- different inference providers (vLLM instead of Ollama) and different MCP connector URLs (k8s service names instead of localhost)
3. **Model name**: Stays `triage-model` everywhere -- it is a logical alias resolved by OGX's routing table
4. **MCP connector IDs**: Stay `mcp::patient-records`, `mcp::medical-knowledge`, `mcp::scheduling` everywhere -- only the backing URLs change in OGX config
