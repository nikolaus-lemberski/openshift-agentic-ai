# Implementation Plan

## Phase Overview

| Phase | What | Effort | Dependencies |
|-------|------|--------|-------------|
| 0 | Local development environment (OGX + Ollama) | 0.5 day | Ollama installed |
| 1 | MCP Servers (self-contained mocks) | 1-2 days | None |
| 2 | Quarkus Chat UI (thin client to Llama Stack) | 0.5-1 day | None (can develop with mock responses) |
| 3 | OpenShift AI model serving | 0.5 day | GPU-enabled OpenShift cluster |
| 4 | Llama Stack OGX via Operator (with MCP connectors) | 0.5-1 day | Phase 1, Phase 3 |
| 5 | Integration and demo rehearsal | 1 day | All above |

Total estimated effort: **4-6 days**

---

## Phase 0: Local Development Environment

Set up the local development stack so all Quarkus apps can be developed and tested locally before deploying to OpenShift. The local environment mirrors production: OGX handles model routing and MCP tool execution, the only differences are the inference provider (Ollama instead of vLLM) and service URLs (localhost instead of k8s services).

See [local-dev-environment.md](local-dev-environment.md) for full setup instructions.

### 0.1 OGX Virtual Environment

Create an isolated Python environment for OGX in `ogx_configs/`:

```bash
cd ogx_configs/
uv venv .venv --python 3.12
uv pip install ogx --python .venv/bin/python
```

### 0.2 Ollama Model

Pull a model with tool-calling support:

```bash
ollama pull granite3.3:8b
```

Alternatively, `qwen3:14b` can be used by setting `OLLAMA_MODEL=qwen3:14b`.

### 0.3 OGX Configuration Files

Two config files in `ogx_configs/`:
- `local-dev.yaml` -- Ollama as inference provider, MCP servers on localhost:8081-8083
- `openshift-prod.yaml` -- vLLM providers, MCP servers as k8s services (reference for the production deployment)

### 0.4 Verify

```bash
# Start OGX with local config
ogx_configs/.venv/bin/ogx stack run ogx_configs/local-dev.yaml

# In another terminal, verify it responds
curl -s http://localhost:8321/v1/models | jq .
```

**Acceptance:** OGX starts on port 8321 and lists `triage-model` backed by the local Ollama model.

---

## Phase 1: MCP Servers

Build three self-contained Quarkus MCP servers. Each is a minimal Quarkus project with one extension (`quarkus-mcp-server-http`) and one Java class. These MCP servers are consumed by **Llama Stack** (not by the Quarkus chat UI).

### 1.1 Patient Records MCP Server

**Project:** `mcp-patient-records/`

**Scaffold:**
```bash
quarkus create app com.redhat.demo:mcp-patient-records \
  --extension='io.quarkiverse.mcp:quarkus-mcp-server-http' \
  --no-code
```

**Implementation:**
- `PatientRecordsMcpServer.java` -- single class with `@Tool`-annotated methods
- `PatientData.java` -- record classes (`Patient`, `MedicalHistory`, `Medication`, `Visit`)
- `SampleDataStore.java` -- in-memory `Map` with 3 pre-loaded patients (Maria Garcia, James Wilson, Sofia Andersson)

**Tools to implement:**

```java
@Tool(description = "Search for a patient by name and date of birth. Returns patient ID and demographics.")
Patient searchPatient(@ToolArg(description = "Patient full name") String name,
                      @ToolArg(description = "Date of birth in YYYY-MM-DD format") String dateOfBirth)

@Tool(description = "Get a patient's medical history including conditions, past diagnoses, and allergies.")
MedicalHistory getPatientHistory(@ToolArg(description = "Patient ID") String patientId)

@Tool(description = "Get a patient's current active medications with dosages.")
List<Medication> getCurrentMedications(@ToolArg(description = "Patient ID") String patientId)

@Tool(description = "Get a patient's recent visit history with dates and notes.")
List<Visit> getRecentVisits(@ToolArg(description = "Patient ID") String patientId)
```

**Acceptance:** `curl http://localhost:8081/mcp` returns MCP server info. Tools discoverable via MCP protocol.

### 1.2 Medical Knowledge MCP Server

**Project:** `mcp-medical-knowledge/`

**Scaffold:**
```bash
quarkus create app com.redhat.demo:mcp-medical-knowledge \
  --extension='io.quarkiverse.mcp:quarkus-mcp-server-http' \
  --no-code
```

**Implementation:**
- `MedicalKnowledgeMcpServer.java` -- `@Tool` methods
- `TriageEngine.java` -- simple rule-based triage logic (if symptoms include X and conditions include Y, then ESI level = Z)
- `DrugInteractionData.java` -- hardcoded interaction table
- `ProtocolData.java` -- sample protocol markdown strings

**Tools to implement:**

```java
@Tool(description = "Assess symptom urgency. Returns ESI triage level (1-5), possible conditions, red flags, and recommended specialty.")
TriageResult triageAssessment(
    @ToolArg(description = "List of current symptoms") List<String> symptoms,
    @ToolArg(description = "Patient age in years") int patientAge,
    @ToolArg(description = "List of existing medical conditions") List<String> existingConditions)

@Tool(description = "Check for drug interactions between current medications and a proposed new medication.")
List<DrugInteraction> checkDrugInteractions(
    @ToolArg(description = "List of current medications") List<String> currentMedications,
    @ToolArg(description = "Proposed new medication to check") String proposedMedication)

@Tool(description = "Get the standard clinical protocol steps for a given condition.")
Protocol getProtocol(@ToolArg(description = "Medical condition name") String condition)
```

**Acceptance:** Triage assessment for "headache + blurred vision + hypertension" returns ESI Level 2 with hypertensive urgency.

### 1.3 Appointment Scheduling MCP Server

**Project:** `mcp-scheduling/`

**Scaffold:**
```bash
quarkus create app com.redhat.demo:mcp-scheduling \
  --extension='io.quarkiverse.mcp:quarkus-mcp-server-http' \
  --no-code
```

**Implementation:**
- `SchedulingMcpServer.java` -- `@Tool` methods
- `ScheduleData.java` -- in-memory schedule with 5 doctors and pre-loaded slots
- `Doctor.java`, `TimeSlot.java`, `Appointment.java` -- record classes

**Tools to implement:**

```java
@Tool(description = "Search available appointment slots by medical specialty and date range.")
List<AvailableSlot> getAvailableSlots(
    @ToolArg(description = "Medical specialty, e.g. cardiology, pulmonology") String specialty,
    @ToolArg(description = "Date range, e.g. 'today' or '2026-05-18 to 2026-05-19'") String dateRange)

@Tool(description = "Get information about a specific doctor including specialty and credentials.")
DoctorInfo getDoctorInfo(@ToolArg(description = "Doctor ID") String doctorId)

@Tool(description = "Book an appointment for a patient with a specific doctor and time slot.")
AppointmentConfirmation bookAppointment(
    @ToolArg(description = "Patient ID") String patientId,
    @ToolArg(description = "Doctor ID") String doctorId,
    @ToolArg(description = "Appointment date and time in ISO format") String dateTime,
    @ToolArg(description = "Urgency level: routine, urgent, emergent") String urgency,
    @ToolArg(description = "Reason for visit") String reason)
```

**Acceptance:** Searching for "cardiology" returns Dr. Chen and Dr. Patel slots. Booking returns a confirmation number.

### Port Assignments (local dev)

| Service | Port |
|---------|------|
| Quarkus Chat UI | 8080 |
| MCP Patient Records | 8081 |
| MCP Medical Knowledge | 8082 |
| MCP Scheduling | 8083 |
| Local OGX server | 8321 (default OGX port) |

---

## Phase 2: Quarkus Chat UI Application

The Quarkus app is now a **thin client**. It does not handle MCP logic or the agentic loop. It sends requests to Llama Stack's `/v1/responses` endpoint and renders the response.

**Project:** `triage-assistant/`

**Scaffold:**
```bash
quarkus create app com.redhat.demo:triage-assistant \
  --extension='rest-client-jackson,websockets-next' \
  --no-code
```

Note: No `langchain4j-openai` or `langchain4j-mcp` extensions needed. The app uses a plain REST client to call Llama Stack.

### 2.1 Llama Stack REST Client

A Quarkus REST Client interface for calling `/v1/responses`:

```java
@RegisterRestClient(configKey = "llamastack")
@Path("/v1")
public interface LlamaStackClient {

    @POST
    @Path("/responses")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ResponseResult createResponse(ResponseRequest request);
}
```

With request/response models:

```java
public record ResponseRequest(
    String model,
    String instructions,
    Object input,       // String or list of messages
    List<ToolConfig> tools
) {}

public record ToolConfig(
    String type,        // "mcp"
    @JsonProperty("connector_id") String connectorId
) {}

public record ResponseResult(
    String id,
    String status,
    List<OutputItem> output
) {}
```

### 2.2 Triage Service

A CDI bean that wraps the REST client with the system prompt and MCP tool configuration:

```java
@ApplicationScoped
public class TriageService {

    @Inject
    @RestClient
    LlamaStackClient llamaStack;

    @ConfigProperty(name = "triage.model", defaultValue = "triage-model")
    String model;

    @ConfigProperty(name = "triage.system-prompt")
    String systemPrompt;

    private static final List<ToolConfig> TOOLS = List.of(
        new ToolConfig("mcp", "mcp::patient-records"),
        new ToolConfig("mcp", "mcp::medical-knowledge"),
        new ToolConfig("mcp", "mcp::scheduling")
    );

    public String chat(String userMessage) {
        var request = new ResponseRequest(model, systemPrompt, userMessage, TOOLS);
        var result = llamaStack.createResponse(request);
        return extractFinalText(result);
    }
}
```

### 2.3 Chat UI

A minimal WebSocket-based chat page:
- `chat.html` -- simple HTML/CSS/JS chat interface (Qute template)
- `ChatWebSocket.java` -- Quarkus WebSocket endpoint that bridges to `TriageService`

The UI should display:
- User messages and agent responses
- A "thinking" indicator while the agent processes
- (Optional) Tool call names extracted from the response output items

### 2.4 Application Configuration

```properties
# Llama Stack connection
quarkus.rest-client.llamastack.url=${LLAMA_STACK_URL:http://localhost:8321}

# Triage configuration
triage.model=${MODEL_NAME:triage-model}
triage.system-prompt=You are a clinical triage assistant for hospital nurses. \
  You help nurses during patient intake by looking up patient records, \
  assessing symptom urgency, checking drug interactions, and scheduling \
  specialist appointments. \
  IMPORTANT: You ASSIST the nurse. You do NOT make clinical decisions. \
  Always state the ESI triage level when assessing symptoms. \
  Flag red flags and recommended immediate actions clearly.
```

Note: **No MCP configuration in the Quarkus app.** MCP connectors are configured in Llama Stack.

### 2.5 Local Dev Profile

For local development:
- Run a local **OGX server** using the venv in `ogx_configs/` with `ogx_configs/local-dev.yaml` -- configured with Ollama as inference provider and the 3 MCP servers as connectors
- Alternatively, for quick iteration on the UI only, mock the Llama Stack response in a dev profile
- MCP servers run locally on ports 8081-8083
- See [docs/local-dev-environment.md](local-dev-environment.md) for full setup details

**Acceptance:** The nurse can run through the full Maria Garcia scenario locally using local OGX + Ollama + 3 MCP servers.

---

## Phase 3: OpenShift AI Model Serving

### 3.1 Prerequisites
- OpenShift 4.16+ with OpenShift AI 3.x installed
- GPU node available (NVIDIA L4 24GB or better)
- Pull secret for `registry.redhat.io` configured on the cluster

### 3.2 Deploy Mistral Small 3.1 24B Instruct (INT4)

Manifests in `k8s/model-serving/`:
- `namespace.yml` -- creates `triage-model-serving` namespace (OpenShift AI Data Science Project)
- `serving-runtime.yml` -- vLLM NVIDIA GPU ServingRuntime for KServe
- `inferenceservice.yml` -- InferenceService CR with OCI/ModelCar storage
- `deploy.sh` -- deployment script that applies resources in order and waits for readiness

Model details:
- **Source:** Red Hat validated ModelCar: `registry.redhat.io/rhelai1/modelcar-mistral-small-3-1-24b-instruct-2503-quantized-w4a16:1.5`
- **Storage:** OCI/ModelCar (no S3/MinIO needed)
- **GPU:** 1x NVIDIA L4 (24GB VRAM), ~14 GiB model + ~5.5 GiB KV cache
- **Tool calling:** `--tool-call-parser=mistral` + `tool_chat_template_mistral3.jinja`
- **Startup:** `--enforce-eager` to skip CUDA graph compilation (fast startup)
- **Access:** Internal only (no Route, `serving.kserve.io/disableRouteCreation: "true"`)

### 3.3 Model Switch (optional, TBD)

If a model switch demo is desired later, a second model can be deployed using the same pattern with a different ModelCar OCI image. Candidates from the Red Hat validated catalog that fit on L4:
- Granite 3.1 8B (FP16): `registry.redhat.io/rhelai1/modelcar-granite-3-1-8b-instruct:1.5`
- Qwen3 8B (FP8): `registry.redhat.io/rhelai1/modelcar-qwen3-8b-fp8-dynamic:1.5`

Since there is only one GPU, models must be swapped (scale down one, deploy the other).

### 3.4 Verify

```bash
# Deploy
./k8s/model-serving/deploy.sh

# Test from within the cluster
oc run curl-test -n triage-model-serving --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://mistral-small-3-1-24b-predictor.triage-model-serving.svc.cluster.local:8080/v1/models
```

---

## Phase 4: Llama Stack OGX via Operator (with MCP Connectors)

Llama Stack manages both model routing AND MCP tool connectors on OpenShift.

### 4.1 Activate the Operator

Enable Llama Stack Operator on the `DataScienceCluster` CR:
```yaml
spec:
  components:
    llamastackoperator:
      managementState: Managed
```

### 4.2 Create LlamaStackDistribution (with Connectors)

Create `k8s/llamastack/llamastack-distribution.yaml`:

```yaml
apiVersion: llamastack.io/v1alpha1
kind: LlamaStackDistribution
metadata:
  name: triage-llamastack
  namespace: triage-assistant
spec:
  # Inference providers pointing to both vLLM instances
  # Routing table with logical alias 'triage-model' -> granite-fast (initially)
  # Both Granite (IBM/Red Hat) and Qwen (Alibaba) as providers -- proves model-agnostic
  # MCP connectors for all three hospital system MCP servers
```

The OGX configuration for production is maintained in `ogx_configs/openshift-prod.yaml`. The `LlamaStackDistribution` CR on OpenShift uses the equivalent configuration with `remote::openai` providers pointing at the vLLM instances and MCP connectors pointing at k8s service names.

The exact CR schema depends on the OpenShift AI version. Refer to:
- [RHOAI 3.4 Llama Stack docs](https://docs.redhat.com/en/documentation/red_hat_openshift_ai_self-managed/3.4/html/working_with_llama_stack/)
- The upstream [OGX docs](https://ogx-ai.github.io/docs)
- The [OGX tools / MCP documentation](https://ogx-ai.github.io/docs/building_applications/tools)

### 4.3 Prepare the Model Switch CR

Create `k8s/llamastack/llamastack-distribution-upgraded.yaml`:
- Same as above, but `triage-model` alias is mapped to `qwen-2.5-32b`
- During the demo, `oc apply -f` this file to switch from Granite (IBM) to Qwen (Alibaba)

### 4.4 Verify

```bash
# List models visible through Llama Stack
curl -s https://llamastack-triage.apps.cluster.example.com/v1/models | jq .

# Test inference (basic, no tools)
curl -s https://llamastack-triage.apps.cluster.example.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model": "triage-model", "messages": [{"role": "user", "content": "Hello"}]}'

# Test /v1/responses with MCP tools (the full agentic loop)
curl -s https://llamastack-triage.apps.cluster.example.com/v1/responses \
  -H "Content-Type: application/json" \
  -d '{
    "model": "triage-model",
    "input": "Search for patient Maria Garcia, born 1978-03-15",
    "tools": [{"type": "mcp", "connector_id": "mcp::patient-records"}]
  }'
```

---

## Phase 5: Integration and Demo Rehearsal

### 5.1 Deploy All Components to OpenShift

- Build container images for: triage-assistant, mcp-patient-records, mcp-medical-knowledge, mcp-scheduling
- Create Deployments, Services, and Routes for each
- MCP server Services must be reachable from the Llama Stack pod (same namespace or cross-namespace networking)
- Quarkus Chat UI only needs connectivity to Llama Stack

### 5.2 End-to-End Test

Run through the full Maria Garcia scenario on the deployed system:
1. Patient lookup (Llama Stack calls Patient Records MCP)
2. Symptom triage (Llama Stack calls Medical Knowledge MCP)
3. Appointment booking (Llama Stack calls Scheduling MCP)
4. Model switch (apply upgraded CR, re-run triage)

### 5.3 Prepare Demo Fallbacks

- **Pre-recorded backup:** Screen-record a successful run in case of live demo failure
- **Local fallback:** Have the full stack running locally with OGX + Ollama in case OpenShift connectivity fails
- **Scripted inputs:** Prepare exact phrases to type during the demo (avoid improvisation for tool-calling reliability)

### 5.4 Timing Rehearsal

Run through the full 45-minute webinar at least twice:
- Verify each act fits within its time budget
- Identify slow transitions (model loading, MCP cold starts, Llama Stack agentic loop duration)
- Pre-warm models before the demo (avoid cold-start delays)

---

## Development Workflow

### Local Development (with local OGX)

See [docs/local-dev-environment.md](local-dev-environment.md) for full setup instructions including OGX virtual environment setup, Ollama model selection, and the OGX configuration files.

**Quick start (separate terminals):**

```
Terminal 1: cd mcp-patient-records && quarkus dev -Dquarkus.http.port=8081
Terminal 2: cd mcp-medical-knowledge && quarkus dev -Dquarkus.http.port=8082
Terminal 3: cd mcp-scheduling && quarkus dev -Dquarkus.http.port=8083
Terminal 4: ollama serve  (with granite3.3:8b or qwen3:14b)
Terminal 5: ogx_configs/.venv/bin/ogx stack run ogx_configs/local-dev.yaml
Terminal 6: cd triage-assistant && quarkus dev
```

Or run `./dev-start.sh` from the project root.

OGX configuration files live in `ogx_configs/`:
- `ogx_configs/local-dev.yaml` -- Ollama provider + localhost MCP servers
- `ogx_configs/openshift-prod.yaml` -- vLLM providers + k8s service MCP servers

Open `http://localhost:8080` and interact with the triage assistant.

### OpenShift Deployment

```bash
# Build and push images
cd mcp-patient-records && quarkus build -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true
cd mcp-medical-knowledge && quarkus build -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true
cd mcp-scheduling && quarkus build -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true
cd triage-assistant && quarkus build -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true

# Deploy to OpenShift
oc apply -f k8s/
```
