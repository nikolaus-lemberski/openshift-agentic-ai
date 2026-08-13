# Agentic AI in regulated environments: with OGX (Llama Stack), Quarkus, and OpenShift AI

## Prerequisite

You need OpenShift AI with a GPU (size tested: g6.12xlarge from AWS). If you have a plain OpenShift cluster and a GPU and need inspiration how to create the hardware profile to make the GPU available, check out this repo:

https://github.com/nikolaus-lemberski/openshift-gen-ai-tutorial

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
├── build.sh                           # Build container images with podman
├── tag_and_push.sh                    # Push container images to registry
├── docs/
│   ├── architecture.md                # Architecture overview and diagrams
│   ├── prd.md                         # Product Requirements Document
│   ├── implementation-plan.md         # Step-by-step implementation plan
│   └── webinar-story.md              # Scripted webinar narrative and demo flow
├── triage-assistant/                  # Quarkus thin chat UI (REST client to Llama Stack + WebSocket)
├── mcp-patient-records/               # Patient Records MCP server
├── mcp-medical-knowledge/             # Medical Knowledge MCP server
├── mcp-scheduling/                    # Appointment Scheduling MCP server
├── scripts/
│   ├── deploy.sh                      # Full infrastructure deployment script
│   └── pull-model.sh                 # Pull model weights into SeaweedFS
└── k8s/                               # OpenShift manifests
    ├── namespace.yml                  # triage-assistant namespace
    ├── model-serving/                 # ServingRuntime + InferenceService for vLLM
    ├── llamastack/                    # LlamaStackDistribution CR + stack config ConfigMap
    ├── seaweedfs/                     # SeaweedFS S3 storage (deployment, PVC, secrets, model pull job)
    ├── apps/                          # Application deployments, services, and routes
    └── openshift-ai/                  # OpenShift AI MCP server integration ConfigMap
```

## Tech Stack

| Layer | Technology | Role |
|-------|-----------|------|
| Chat UI | Quarkus + WebSockets | Thin UI, delegates all AI to Llama Stack |
| AI Middleware | Llama Stack (OGX) via Operator | Model routing, MCP tool execution, agentic loop (`/v1/responses`) |
| Model Serving | OpenShift AI + vLLM | On-premise LLM hosting (Qwen 3.5 9B via S3/SeaweedFS) |
| Agent Tools | MCP Servers (Quarkus) | Patient records, medical knowledge, scheduling |
| Platform | OpenShift | Container orchestration, GPU management |

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         User / Browser                              │
└────────────────────────────┬────────────────────────────────────────┘
                             │ WebSocket / REST
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Triage Assistant (Quarkus)                       │
│              Thin UI - Delegates all AI to Llama Stack              │
└────────────────────────────┬────────────────────────────────────────┘
                             │ /v1/responses
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  Llama Stack (OGX) - AI Middleware                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ Agentic Loop:                                                 │  │
│  │  1. Route inference to model                                  │  │
│  │  2. Execute tool calls via MCP                                │  │
│  │  3. Feed results back to model                                │  │
│  │  4. Repeat until complete                                     │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────┬────────────────────────────────────┬──────────────────────────┘
      │                                    │
      │ Model Inference                    │ MCP Protocol
      ▼                                    ▼
┌──────────────────────┐      ┌────────────────────────────────────────┐
│  OpenShift AI/vLLM   │      │        MCP Servers (Quarkus)           │
│                      │      │  ┌──────────────────────────────────┐  │
│  ┌────────────────┐  │      │  │  mcp-patient-records             │  │
│  │ Qwen 3.5 9B    │  │      │  │  - Get patient info              │  │
│  │ (S3/SeaweedFS) │  │      │  │  - Search by symptoms            │  │
│  └────────────────┘  │      │  └──────────────────────────────────┘  │
│                      │      │  ┌──────────────────────────────────┐  │
│  GPU: g6.12xlarge    │      │  │  mcp-medical-knowledge           │  │
└──────────────────────┘      │  │  - Query knowledge base          │  │
                              │  │  - Get treatment protocols       │  │
                              │  └──────────────────────────────────┘  │
                              │  ┌──────────────────────────────────┐  │
                              │  │  mcp-scheduling                  │  │
                              │  │  - Check availability            │  │
                              │  │  - Book appointments             │  │
                              │  └──────────────────────────────────┘  │
                              └────────────────────────────────────────┘
═════════════════════════════════════════════════════════════════════════
                      OpenShift + OpenShift AI
                   Container Orchestration & GPU Management
```

## Key Architectural Insight

Llama Stack (OGX) is **not** just a model router -- it is the **full AI middleware**. The Quarkus application sends a user message to Llama Stack's `/v1/responses` endpoint. Llama Stack then:

1. Routes inference to the configured model (via its routing table)
2. When the model requests tool calls, executes them server-side via MCP connectors
3. Feeds tool results back to the model
4. Repeats until the agentic loop completes
5. Returns the final response to the Quarkus app

This means both **model switching** and **tool switching** are platform operations managed through Llama Stack configuration -- the application never changes.

## Local Testing

The MCP servers are self-contained Quarkus apps with in-memory sample data and no external dependencies. Run any MCP server with `quarkus dev` and test it with [MCP Inspector](https://github.com/modelcontextprotocol/inspector):

```bash
cd mcp-patient-records && quarkus dev -Dquarkus.http.port=8081

# In another terminal, start MCP Inspector and connect to the server
npx @modelcontextprotocol/inspector --url http://localhost:8081/mcp
```

The triage-assistant is a thin REST client to Llama Stack and requires the full stack on OpenShift for end-to-end testing.

## Build and Deploy

Build container images, push them to the registry, and deploy the full infrastructure to OpenShift:

```bash
./build.sh
./tag_and_push.sh
./scripts/deploy.sh
```

The deploy script provisions everything (namespace, SeaweedFS, model serving, Llama Stack, PromptGuard, application deployments) and waits for each component to become ready. See `docs/webinar-story.md` for the demo flow.
