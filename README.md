# Agentic AI in regulated environments: with OGX (Llama Stack), Quarkus, and OpenShift AI

An open-source stack for running on-premise agentic AI with centrally governed models, MCP tool execution, and secure enterprise integrations. A healthcare Patient Triage Assistant demonstrates the architecture. See [ABSTRACT.md](ABSTRACT.md) for full webinar description.

## Prerequisite

OpenShift AI with GPU (tested: g6.12xlarge from AWS). For hardware profile setup, see: https://github.com/nikolaus-lemberski/openshift-gen-ai-tutorial

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
│  │  1. PromptGuard safety filter                                 │  │
│  │  2. Route inference to model                                  │  │
│  │  3. Execute tool calls via MCP                                │  │
│  │  4. Feed results back to model                                │  │
│  │  5. Repeat until complete                                     │  │
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
│  ┌────────────────┐  │      │  │  mcp-medical-knowledge           │  │
│  │ PromptGuard    │  │      │  │  - Query knowledge base          │  │
│  │ 86M            │  │      │  │  - Get treatment protocols       │  │
│  └────────────────┘  │      │  └──────────────────────────────────┘  │
│                      │      │  ┌──────────────────────────────────┐  │
│  GPU: g6.12xlarge    │      │  │  mcp-scheduling                  │  │
└──────────────────────┘      │  │  - Check availability            │  │
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

The deploy script provisions everything (namespace, SeaweedFS, model serving, Llama Stack, PromptGuard, application deployments) and waits for each component to become ready. 

**For demo workflow and testing instructions, see `docs/webinar-story.md`.**
