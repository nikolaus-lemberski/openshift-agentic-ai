# Agentic AI in regulated environments: with OGX (Llama Stack), Quarkus, and OpenShift AI

RHPDS: https://catalog.demo.redhat.com/catalog/all?search=OpenShift+AI+3&item=babylon-catalog-prod%2Fpublished.openshift-ai-v3.prod
Campaign ID: 701Pe00000tj4MLIAY

## Abstract

Healthcare, finance, public sector -- or any organization that needs to own its data and not depend on external model providers -- these environments need AI agents that run fully on-premise, with centrally governed models and tools, and secure access to internal systems.

This webinar presents an open-source stack for running agentic AI on-prem: **OpenShift AI** to serve models via an inference server (**vLLM**), **OGX (Llama Stack)** as full AI middleware handling model routing, MCP tool execution, and server-side agentic orchestration. **MCP Servers** built with **Quarkus** connect the agent to enterprise backends. A healthcare Patient Triage Assistant serves as the live demo, but the architecture applies to any regulated or data-sovereign environment.

Target audience: Developer, AI engineers, Platform engineers

## German

### Title

Agentic AI in regulierten Umgebungen: mit OGX (Llama Stack), Quarkus und OpenShift AI

### Abstract

Gesundheitswesen, Finanzbranche, öffentliche Hand - oder jede Organisation, die ihre Daten selbst kontrollieren und nicht von externen Modellanbietern abhängig sein will - diese Umgebungen brauchen KI-Agenten, die vollständig On-Premise laufen, mit zentral verwalteten Modellen und Werkzeugen sowie sicherem Zugriff auf interne Systeme.

Dieses Webinar stellt einen Open-Source-Stack für agentische KI On-Premise vor: **OpenShift AI** um Modelle über einen inference server (**vLLM**) bereitzustellen, **OGX (Llama Stack)** als vollständige KI-Middleware für Model-Routing, MCP-Werkzeugausführung und serverseitige agentische Orchestrierung. **MCP-Server** mit **Quarkus** verbinden den Agenten mit Unternehmens-Backends. Ein Patienten-Triage-Assistent dient als Live-Demo, aber die Architektur ist auf jede regulierte oder datensouveräne Umgebung übertragbar.

Empfohlen für: Entwickler, AI engineers, Platform engineers

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
| Model Serving | OpenShift AI + vLLM | On-premise LLM hosting (Mistral Small 3.1 24B INT4 via ModelCar) |
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
