# Product Requirements Document: Patient Triage Assistant

## 1. Overview

### 1.1 Product Name
**Patient Triage Assistant** -- an AI-powered clinical intake assistant for hospital triage nurses.

### 1.2 Purpose
Demonstrate a production-grade agentic AI system built on an open-source stack (Quarkus, Llama Stack OGX, OpenShift AI) that addresses real constraints in regulated healthcare environments: data sovereignty, model governance, tool governance, and secure enterprise integration.

### 1.3 Context
This is a **webinar demo application**. The MCP servers use self-contained mock data rather than real hospital backends. The architecture, interfaces, and deployment model are production-representative; the data is synthetic.

### 1.4 Architectural Premise
**Llama Stack (OGX) is the full AI middleware**, not just a model router. It handles:
- Model routing (logical aliases mapped to vLLM backends)
- MCP tool execution (server-side, via connectors)
- Agentic orchestration (the inference-tool_call-re-inference loop runs inside Llama Stack via `/v1/responses`)

The Quarkus application is a **thin chat UI** that sends requests to Llama Stack and renders responses. It does not handle MCP client logic or the agentic loop.

### 1.5 Success Criteria
- A live demo runs through a complete patient triage scenario (lookup, triage, booking) without failure
- Model switching is demonstrated by changing a Kubernetes CR with zero application changes
- Tool switching is demonstrated by showing that MCP connectors are managed in Llama Stack config, not in the application
- The audience understands why Llama Stack as full middleware is superior to a thin gateway approach

---

## 2. Users and Personas

### 2.1 Primary User: Triage Nurse
- **Role:** Registered nurse performing patient intake at a hospital or urgent care clinic
- **Goal:** Quickly assess patient urgency, access medical history, and route to the right specialist
- **Context:** Uses the assistant on a workstation during a face-to-face or phone intake
- **Constraint:** The nurse makes all clinical decisions; the AI assists but never acts autonomously

### 2.2 Secondary User: Platform Operations Team
- **Role:** Hospital IT / platform engineering
- **Goal:** Manage which AI models and tools are available, perform model upgrades, add new MCP connectors, monitor system health
- **Context:** Operates via OpenShift console and `oc` CLI
- **Constraint:** Must be able to swap models AND tools without involving application developers

### 2.3 Tertiary User: AI Safety Committee
- **Role:** Clinical and technical reviewers who approve models for clinical use
- **Goal:** Ensure only validated models serve clinical recommendations; control which hospital systems the AI agent can access
- **Context:** Reviews model evaluation reports, approves models and tool access for specific use cases
- **Constraint:** Needs audit trail of which model version produced which recommendations, and which tools were invoked

---

## 3. Functional Requirements

### 3.1 Chat Interface (Quarkus App)

| ID | Requirement | Priority |
|----|------------|----------|
| F-01 | The application provides a web-based chat interface accessible from a browser | Must |
| F-02 | The nurse can type natural language messages and receive structured responses | Must |
| F-03 | The interface displays which tools the agent invoked (transparency) | Should |
| F-04 | The interface supports a conversation history within a session | Must |
| F-05 | The interface displays a visual indicator while the agent is processing | Should |
| F-06 | The application sends requests to Llama Stack's `/v1/responses` endpoint | Must |
| F-07 | The application does NOT contain MCP client logic -- all tool execution is delegated to Llama Stack | Must |

### 3.2 Patient Records (via MCP, executed by Llama Stack)

| ID | Requirement | Priority |
|----|------------|----------|
| F-10 | The agent can search for a patient by name and date of birth | Must |
| F-11 | The agent can retrieve a patient's medical history (conditions, allergies, past diagnoses) | Must |
| F-12 | The agent can retrieve a patient's current medications with dosages | Must |
| F-13 | The agent can retrieve a patient's recent visit history with notes | Should |
| F-14 | Sample data includes at least 3 patients with diverse medical profiles | Must |

### 3.3 Clinical Triage (via MCP, executed by Llama Stack)

| ID | Requirement | Priority |
|----|------------|----------|
| F-20 | The agent can assess symptom urgency and return an ESI triage level (1-5) | Must |
| F-21 | The triage assessment considers patient age, existing conditions, and current symptoms | Must |
| F-22 | The agent can check drug interactions between current medications and a proposed medication | Must |
| F-23 | The agent can retrieve clinical protocol steps for a given condition | Should |
| F-24 | Triage responses include red flags and recommended immediate actions | Must |

### 3.4 Appointment Scheduling (via MCP, executed by Llama Stack)

| ID | Requirement | Priority |
|----|------------|----------|
| F-30 | The agent can search available appointment slots by medical specialty and date range | Must |
| F-31 | The agent can retrieve doctor information (specialty, credentials) | Should |
| F-32 | The agent can book an appointment and return a confirmation number | Must |
| F-33 | Bookings include urgency level and reason for visit | Should |

### 3.5 Model Routing and Switching (Llama Stack)

| ID | Requirement | Priority |
|----|------------|----------|
| F-40 | The application connects to Llama Stack OGX via the `/v1/responses` endpoint | Must |
| F-41 | The application uses a logical model alias (e.g., `triage-model`), not a hardcoded model name | Must |
| F-42 | Changing the model behind the alias requires only a `LlamaStackDistribution` CR update | Must |
| F-43 | Model switching requires zero application restarts or redeployments | Must |
| F-44 | Both a small/fast and a large/powerful model are available for demonstration | Must |

### 3.6 Tool Management (Llama Stack)

| ID | Requirement | Priority |
|----|------------|----------|
| F-50 | MCP servers are registered as connectors in Llama Stack configuration | Must |
| F-51 | Llama Stack discovers and executes MCP tools server-side during the agentic loop | Must |
| F-52 | Adding a new MCP connector does not require changes to the Quarkus application | Must |
| F-53 | The Quarkus app references MCP tools by connector ID in the `/v1/responses` request | Must |

---

## 4. Non-Functional Requirements

| ID | Requirement | Priority |
|----|------------|----------|
| NF-01 | All patient data processing (inference + tool execution) occurs within the OpenShift cluster | Must |
| NF-02 | The Quarkus application starts in under 5 seconds (JVM mode) | Should |
| NF-03 | MCP servers respond within 200ms (mock data, no real backend latency) | Should |
| NF-04 | The system is deployable on OpenShift 4.16+ with OpenShift AI 3.x | Must |
| NF-05 | All components are containerized with Dockerfile/Containerfile | Must |
| NF-06 | The demo can run locally (dev mode) with a local OGX server using Ollama as inference provider | Should |
| NF-07 | Tool execution never passes through the Quarkus app -- data flows Llama Stack <-> MCP server directly | Must |

---

## 5. Sample Data Specification

### 5.1 Patients

**Patient 1: Maria Garcia** (primary demo patient)
- Age: 48, DOB: 1978-03-15
- Conditions: Hypertension (diagnosed 2019)
- Allergies: Penicillin
- Medications: Lisinopril 20mg daily
- Recent visits: March 2026 routine BP check (135/85, stable)

**Patient 2: James Wilson**
- Age: 72, DOB: 1954-08-22
- Conditions: Type 2 Diabetes, COPD
- Allergies: None known
- Medications: Metformin 1000mg twice daily, Tiotropium inhaler
- Recent visits: April 2026 diabetes management (A1C: 7.2)

**Patient 3: Sofia Andersson**
- Age: 31, DOB: 1995-01-10
- Conditions: Pregnancy (28 weeks)
- Allergies: Latex
- Medications: Prenatal vitamins, Iron supplement
- Recent visits: May 2026 prenatal checkup (routine, normal)

### 5.2 Triage Rules (simplified for demo)

| Symptoms + Context | ESI Level | Assessment |
|--------------------|-----------|------------|
| Headache + blurred vision + BP > 170/100 + hypertension history | 2 (Emergent) | Hypertensive urgency, possible end-organ involvement |
| Shortness of breath + COPD history + SpO2 < 92% | 2 (Emergent) | COPD exacerbation, respiratory distress |
| Abdominal pain + pregnancy > 20 weeks | 2-3 (contextual) | Obstetric emergency vs. common pregnancy discomfort |
| Mild headache, no red flags | 4-5 (Non-urgent) | Tension headache, routine follow-up |

### 5.3 Drug Interactions (simplified for demo)

| Drug A | Drug B | Severity | Warning |
|--------|--------|----------|---------|
| Lisinopril | NSAIDs (ibuprofen) | Moderate | Reduced antihypertensive effect, increased renal risk |
| Lisinopril | Potassium supplements | Major | Risk of hyperkalemia |
| Metformin | Contrast dye | Major | Risk of lactic acidosis; hold 48h before/after |
| Metformin | Alcohol | Moderate | Increased risk of hypoglycemia and lactic acidosis |

### 5.4 Doctors / Schedule

| Doctor | Specialty | Available Slots (demo) |
|--------|-----------|----------------------|
| Dr. Sarah Chen | Cardiology (hypertensive heart disease) | Today 2:30 PM, Tomorrow 9:00 AM |
| Dr. Raj Patel | Cardiology (general) | Today 4:00 PM |
| Dr. Emily Torres | Pulmonology | Tomorrow 10:00 AM, Tomorrow 2:00 PM |
| Dr. Michael O'Brien | Obstetrics | Today 3:00 PM, Tomorrow 11:00 AM |
| Dr. Anna Kowalski | Internal Medicine | Today 1:00 PM, Tomorrow 8:30 AM |

---

## 6. Technical Constraints

| Constraint | Detail |
|-----------|--------|
| Java version | 21+ (Quarkus requirement for virtual threads) |
| Quarkus version | Latest LTS (3.x) |
| MCP transport | Streamable HTTP (preferred over deprecated SSE) |
| Llama Stack API | `/v1/responses` for server-side agentic orchestration |
| Model API | OpenAI-compatible (`/v1/chat/completions`, `/v1/models`) between Llama Stack and vLLM |
| Container runtime | Podman or Docker |
| GPU requirement | At least 1x NVIDIA A10G (24GB) for Granite 8B; 1x A100 80GB (or 2x A10G) for Qwen 2.5 32B |
| OpenShift version | 4.16+ with OpenShift AI 3.x |

---

## 7. Out of Scope (for the webinar demo)

- Real EHR/EMR integration (FHIR, HL7)
- User authentication and RBAC on the chat interface
- Persistent conversation storage
- Multi-tenant support
- Production-grade error handling and circuit breakers
- Llama Stack safety shields / guardrails (stretch goal)
- Audit trail implementation (discussed conceptually, not built)
- Load testing and performance benchmarking
- LangChain4j `@RegisterAiService` / `@McpToolBox` (not needed -- Llama Stack handles orchestration)
