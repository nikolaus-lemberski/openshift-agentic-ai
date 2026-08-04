# Patient Triage Assistant — Agentic AI Webinar Demo

45-minute webinar demo: agentic AI in healthcare on an open-source stack. A **Patient Triage Assistant** helps triage nurses during intake — patient lookup, symptom urgency, drug interactions, specialist booking. **Demo app with self-contained mock data** (MCP servers use in-memory samples, not real backends).

## Architecture

**Llama Stack (OGX) is the full AI middleware**, not just a model router:
1. Model routing — inference routed to vLLM via `remote::vllm`
2. MCP tool execution — MCP servers registered as connectors, executed server-side
3. Agentic orchestration — inference → tool_call → execute → re-inference loop inside Llama Stack via `/v1/responses`

The Quarkus app is a **thin chat UI** — calls Llama Stack `/v1/responses`, renders the response. It does NOT contain MCP client logic, LangChain4j, or agentic loop management. One request in, one final response out.

## Tech stack

| Layer | Tech | Purpose |
|-------|------|---------|
| Chat UI | Quarkus + WebSockets + REST Client | Thin UI → Llama Stack `/v1/responses` |
| AI Middleware | Llama Stack (OGX) via Operator | Model routing, MCP connectors, server-side agentic loop |
| Model Serving | OpenShift AI 3.4 + vLLM | On-prem LLM hosting (Qwen 3.5 9B via S3/SeaweedFS) |
| Agent Tools | 3× Quarkus MCP Servers (Streamable HTTP) | Patient records, medical knowledge, scheduling |
| Platform | OpenShift 4.16+ | Container orchestration |

## Project structure

```
triage-assistant/           Quarkus thin chat UI (REST client → Llama Stack + WebSocket)
mcp-patient-records/        MCP server: patient lookup, history, medications
mcp-medical-knowledge/      MCP server: triage assessment, drug interactions, protocols
mcp-scheduling/             MCP server: appointment availability and booking
build.sh                    Build container images with podman
tag_and_push.sh             Push container images to registry
scripts/
  deploy.sh                 Full infrastructure deployment script
  pull-model.sh             Pull model weights into SeaweedFS
k8s/                        Kubernetes manifests
  namespace.yml             triage-assistant namespace
  model-serving/            ServingRuntime + InferenceService for vLLM on OpenShift AI
  llamastack/               ConfigMap + LlamaStackDistribution CR
  seaweedfs/                SeaweedFS S3 storage (deployment, PVC, secrets, model pull job)
  apps/                     Application deployments, services, routes
  openshift-ai/             OpenShift AI MCP server integration ConfigMap
docs/                       Architecture, PRD, implementation plan, webinar script
```

## Key design decisions

1. **Llama Stack is the full middleware.** The Quarkus app does NOT handle MCP client logic. Tool discovery, execution, and the agentic loop are server-side via `/v1/responses`. Model and tool switching are platform ops, not code changes.
2. **MCP servers are self-contained mocks.** Hardcoded sample data in memory. No databases, no external deps. In production, only the `@Tool` implementation changes.
3. **No LangChain4j.** Plain Quarkus REST Client to Llama Stack. No framework coupling.
4. **Configurable model name.** `MODEL_NAME` env var. On OpenShift: `qwen-vllm/qwen35-9b`. Locally: `triage-model` alias. Switching = ConfigMap + Llama Stack restart.
5. **MCP connectors in Llama Stack config**, not in the Quarkus app. App references connector IDs (`mcp::patient-records`, etc.) in `/v1/responses`.
6. **Three separate MCP servers** (not one monolith) to demonstrate composability.
7. **Streamable HTTP transport** for all MCP servers (not deprecated SSE, not STDIO).

## Coding conventions

- Java 21+ with Quarkus 3.x LTS
- `@RegisterRestClient` for Llama Stack calls
- Quarkus WebSockets Next for the chat UI
- MCP servers use `io.quarkiverse.mcp:quarkus-mcp-server-http` with `@Tool` / `@ToolArg`
- Java records for DTOs
- Config via `application.properties` with env var overrides
- Keep code minimal and readable — it will be shown on screen during the webinar

## Sample data

**Patients** (Patient Records MCP):
- Maria Garcia (P-1042): 48, hypertension, lisinopril 20mg, penicillin allergy — primary demo patient
- James Wilson (P-2187): 72, T2 diabetes + COPD, metformin + tiotropium
- Sofia Andersson (P-3291): 31, pregnant 28 weeks, prenatal vitamins

**Doctors** (Scheduling MCP):
- Dr. Sarah Chen (cardiology), Dr. Raj Patel (cardiology), Dr. Emily Torres (pulmonology), Dr. Michael O'Brien (obstetrics), Dr. Anna Kowalski (internal medicine)

**Demo scenario:** Maria Garcia → lookup → triage (headaches + blurred vision + BP 180/110 = hypertensive urgency, ESI Level 2) → book urgent cardiology with Dr. Chen.

## Local testing

- MCP servers: run with `quarkus dev`, test with MCP Inspector against Streamable HTTP endpoint (e.g. `http://localhost:8081/mcp`)
- triage-assistant: requires running Llama Stack instance, tested end-to-end on OpenShift

## Reference docs

- `docs/webinar-story.md` — full scripted demo flow
- `docs/prd.md` — detailed sample data specifications
- `docs/architecture.md` — diagrams and data flow
