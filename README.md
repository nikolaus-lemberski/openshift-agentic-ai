# Agentic AI in Regulated Environments: with Llama Stack / OGX, Quarkus, and OpenShift AI

## Abstract

Regulated industries need AI agents that run on-premise, with centrally governed models and tools, and secure access to internal enterprise systems. This webinar shows how -- using a healthcare Patient Triage Assistant as a live demo.

We build an AI agent that helps triage nurses look up patient records, assess symptom urgency, check drug interactions, and book specialist appointments. The stack: **OpenShift AI** hosts models on-premise via vLLM, **Llama Stack (OGX)** serves as full AI middleware handling model routing, MCP tool execution, and server-side agentic orchestration, **MCP Servers** built with **Quarkus** connect the agent to hospital systems, and a thin Quarkus chat UI ties it together.

The key demo: switching from IBM Granite to Alibaba Qwen -- a different model vendor -- by updating a single Kubernetes CR. Zero code changes, zero downtime, zero vendor lock-in.

**Duration:** 45 minutes | **Audience:** Enterprise architects, platform engineers, Java developers

## Project Structure

```
.
├── README.md                          # This file
├── docs/
│   ├── architecture.md                # Architecture overview and diagrams
│   ├── prd.md                         # Product Requirements Document
│   ├── implementation-plan.md         # Step-by-step implementation plan
│   ├── local-dev-environment.md       # Local dev setup with Ollama + OGX
│   └── webinar-story.md              # Scripted webinar narrative and demo flow
├── ogx_configs/                       # OGX (Llama Stack) configuration
│   ├── .venv/                         # Python venv with OGX (created via uv, gitignored)
│   ├── local-dev.yaml                 # OGX config: Ollama + localhost MCP servers
│   └── openshift-prod.yaml            # OGX config: vLLM + k8s service MCP servers
├── dev-start.sh                       # Startup script for local development
├── triage-assistant/                  # Quarkus chat UI application (TODO)
├── mcp-patient-records/               # Patient Records MCP server (TODO)
├── mcp-medical-knowledge/             # Medical Knowledge MCP server (TODO)
├── mcp-scheduling/                    # Appointment Scheduling MCP server (TODO)
└── k8s/                               # OpenShift / Llama Stack manifests (TODO)
    ├── model-serving/                 # InferenceService CRs for vLLM
    └── llamastack/                    # LlamaStackDistribution CR + connector config
```

## Tech Stack

| Layer | Technology | Role |
|-------|-----------|------|
| Chat UI | Quarkus + WebSockets | Thin UI, delegates all AI to Llama Stack |
| AI Middleware | Llama Stack (OGX) via Operator | Model routing, MCP tool execution, agentic loop (`/v1/responses`) |
| Model Serving | OpenShift AI + vLLM | On-premise LLM hosting (Granite 3.3 8B + Qwen 2.5 32B) |
| Agent Tools | MCP Servers (Quarkus) | Patient records, medical knowledge, scheduling |
| Platform | OpenShift | Container orchestration, GPU management |

## Key Architectural Insight

Llama Stack (OGX) is **not** just a model router -- it is the **full AI middleware**. The Quarkus application sends a user message to Llama Stack's `/v1/responses` endpoint. Llama Stack then:

1. Routes inference to the configured model (via its routing table)
2. When the model requests tool calls, executes them server-side via MCP connectors
3. Feeds tool results back to the model
4. Repeats until the agentic loop completes
5. Returns the final response to the Quarkus app

This means both **model switching** and **tool switching** are platform operations managed through Llama Stack configuration -- the application never changes.
