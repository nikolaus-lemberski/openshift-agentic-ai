# Webinar Story: Scripted Narrative and Demo Flow

## Webinar Title

**Agentic AI in Regulated Environments: with Llama Stack / OGX, Quarkus, and OpenShift AI**

## Duration

45 minutes total: ~30 minutes presentation + live demo, ~15 minutes Q&A

---

## Act 1: Why Healthcare AI Needs This Stack (8 min)

### Opening (3 min)

**[SLIDE: Title slide]**

> "Today we're going to build something real. Not a chatbot that answers trivia -- an AI agent that helps a triage nurse save time during patient intake. It can look up medical records, assess how urgent a patient's symptoms are, check for drug interactions, and book specialist appointments. All through natural language."

> "But here's the catch: this is healthcare. We can't just call OpenAI's API. Patient data -- symptoms, medications, medical history -- is Protected Health Information under HIPAA. It cannot leave the hospital's infrastructure. Ever."

> "So we need to host our own models, govern which models are approved for clinical use, and connect the AI agent to the hospital's internal systems -- the EHR, the scheduling system, the clinical knowledge base. These are all behind the firewall."

> "And here's what makes this different from most agentic AI demos you've seen: we're not going to wire all of this together in application code. We're going to let Llama Stack -- our AI middleware -- handle everything: model routing, tool execution, the agentic loop. The application is just a chat window."

> "That's what today is about. Let me show you the stack."

### Architecture Walkthrough (5 min)

**[SLIDE: Architecture diagram]**

Walk through each layer bottom-up:

1. **OpenShift AI + vLLM** -- "Our model runs here. Inside the hospital's data center, on the hospital's GPUs. We're running Qwen 3.6 27B -- a powerful dense model in FP8 precision, served by vLLM with tensor parallelism across 4 GPUs. It has excellent tool-calling capabilities."

2. **MCP Servers** -- "These are the agent's hands. Three Quarkus microservices exposing hospital systems as MCP tools: patient records, medical knowledge, and appointment scheduling. They speak the Model Context Protocol -- an open standard for tool integration."

3. **Llama Stack (OGX) via Operator** -- "This is the brain of the system. Not the application -- the *middleware*. Llama Stack provides one endpoint: `/v1/responses`. When our app sends a user message, Llama Stack runs the entire agentic loop server-side: it routes inference to the right model, the model decides which tools to call, Llama Stack executes those tools via MCP, feeds the results back to the model, and repeats until it has a final answer. All inside the cluster."

4. **Quarkus Chat UI** -- "And this is the application. It's intentionally thin. A WebSocket chat page that sends the nurse's message to Llama Stack and renders the response. No MCP client code. No agentic loop logic. Just a REST call."

**Key message:** "The governance story is what makes this real. The platform team controls which models AND which tools the agent can use -- through Kubernetes custom resources and Llama Stack config. The application team builds the UI. Swapping a model is an `oc apply`. Adding a new tool is a config change. No PRs, no CI/CD, no redeployments."

---

## Act 2: The AI Platform on OpenShift (10 min)

### Model Hosting on OpenShift AI (5 min)

**[SWITCH TO: OpenShift Console or terminal]**

> "Let's start with the model. I have Qwen 3.6 27B deployed on OpenShift AI, served by vLLM. It's a 27-billion parameter dense model in FP8 precision, running across 4 GPUs with tensor parallelism."

**Show:**
1. The OpenShift AI dashboard with the `InferenceService` in the `triage-assistant` project
2. Or the terminal: `oc get inferenceservices -n triage-assistant`

```
NAME          READY   URL
qwen36-27b    True    http://qwen36-27b-predictor.triage-assistant.svc.cluster.local:8080
```

**Quick test (from a temp pod since it's internal-only):**
```bash
oc run curl-test -n triage-assistant --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://qwen36-27b-predictor.triage-assistant.svc.cluster.local:8080/v1/models
# {"data":[{"id":"qwen36-27b",...}]}
```

> "The model is internal-only -- no external Route, accessible only within the cluster. Llama Stack connects to it via the Kubernetes Service URL. Patient data never leaves the cluster."

> "The model weights are stored in a MinIO S3 bucket within the cluster. We pulled the Qwen 3.6 27B FP8 weights and uploaded them to MinIO, then the InferenceService pulls from there. Everything stays inside the cluster."

### Llama Stack via Operator (5 min)

**Show:**
1. The `LlamaStackDistribution` CR YAML

> "Now let's look at Llama Stack. This is the custom resource that the Llama Stack Operator manages. And this is where it gets interesting."

```bash
oc get llamastackdistribution -n triage-assistant
# NAME                  READY
# triage-llamastack     True
```

**Show the ConfigMap content (highlight BOTH inference provider and connectors):**

> "Two things to notice. First, the inference provider: `remote::vllm` pointing at our Qwen 3.6 27B InferenceService. Second -- and this is new if you've only seen Llama Stack as a gateway -- the **connectors** section. Three MCP servers are registered here: patient records, medical knowledge, and scheduling. Llama Stack will discover their tools and execute them server-side during the agentic loop."

> "The application doesn't know about any of this. It just calls `/v1/responses` with the model name and a list of connector IDs. Llama Stack handles everything else."

**Quick test:**
```bash
oc run curl-test -n triage-assistant --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://triage-llamastack-service:8321/v1/models
# {"data":[{"id":"qwen-vllm/qwen36-27b",...}]}
```

> "Qwen 3.6 27B accessible through Llama Stack. And behind the scenes, three MCP servers registered and ready."

---

## Act 3: The Agent Helps a Nurse (15 min)

### Quick Code Tour (3 min)

**[SWITCH TO: IDE showing the Quarkus project]**

> "Before we run the demo, let me show you the code. And I want you to notice how little there is."

**Show `TriageService.java`:**

> "This is the entire AI service. A REST client call to Llama Stack's `/v1/responses` endpoint. The system prompt. The connector IDs for MCP tools. That's it. No MCP client. No agentic loop. No LangChain4j. Just a REST call."

**Show `application.properties`:**

> "The configuration: one URL to Llama Stack, a model name, and the system prompt. No MCP server URLs -- those are configured in Llama Stack, not here. The application is decoupled from both the models and the tools."

**Show one MCP server (`PatientRecordsMcpServer.java`):**

> "And here's what an MCP server looks like in Quarkus. A Java class with `@Tool` annotations. Each method becomes a tool that Llama Stack discovers and executes. This one searches patients and retrieves medical histories. For the demo it's backed by sample data, but in production it would call the hospital's EHR API."

> "Notice: this MCP server doesn't know who's calling it. It could be Llama Stack, it could be another MCP client, it could be an IDE plugin. That's the beauty of MCP -- it's a standard."

### Interaction 1: Patient Lookup (4 min)

**[SWITCH TO: Browser with the chat UI]**

> "Let's play the role of a triage nurse. A patient has just arrived."

**Type:**
> `Patient Maria Garcia, date of birth March 15 1978, is here for intake. Pull up her records.`

**Wait for response. Narrate what's happening:**

> "Our Quarkus app just sent that message to Llama Stack's `/v1/responses` endpoint. Llama Stack is now running the agentic loop server-side. The model decided to call `searchPatient`... Llama Stack is executing that tool via MCP against the Patient Records server... found her. Now `getPatientHistory`, `getCurrentMedications`... all happening inside Llama Stack, inside the cluster."

**Expected response (summarized):**
> Maria Garcia, 48. History: hypertension (2019), penicillin allergy. Current medication: Lisinopril 20mg daily. Last visit: March 2026, routine BP check, readings stable at 135/85.

> "That came back as one complete response. The Quarkus app made one REST call and got back the final answer. All the tool orchestration happened server-side. Let's give it something harder."

### Interaction 2: Symptom Triage (5 min)

**Type:**
> `She's complaining of persistent headaches and blurred vision for the past two weeks. Her blood pressure reading just now is 180/110. What's your assessment?`

**Wait for response. This is the big moment.**

**Narrate:**
> "Llama Stack is running the agentic loop again. The model is deciding which tools to call... `triageAssessment` on the Medical Knowledge server... `checkDrugInteractions`... `getProtocol` for hypertensive urgency... All executed server-side by Llama Stack via MCP."

**Expected response (summarized):**
> ESI Triage Level 2 (Emergent). Red flags: BP 180/110 in a hypertensive patient with neurological symptoms (headache + visual changes). Possible hypertensive urgency with end-organ involvement. Immediate actions: confirm BP with manual cuff, assess for encephalopathy signs, ECG + basic labs, consider IV antihypertensive. Verify lisinopril compliance. Recommend urgent cardiology evaluation today.

> "A structured, actionable triage assessment. The agent correlated symptoms with medical history and medications, pulled the clinical protocol, and gave concrete next steps. The nurse makes the clinical decision -- but the AI just saved five minutes of chart review."

> "And remember: our Quarkus app didn't execute a single tool call. It made one REST call to Llama Stack and got this back."

### Interaction 3: Book the Specialist (3 min)

**Type:**
> `Find me an available cardiologist today or tomorrow.`

**Expected response:**
> Available: Dr. Sarah Chen today 2:30 PM, Dr. Raj Patel today 4:00 PM, Dr. Sarah Chen tomorrow 9:00 AM. Dr. Chen specializes in hypertensive heart disease.

**Type:**
> `Book Dr. Chen today at 2:30. Flag it as urgent and include the BP reading and symptoms.`

**Expected response:**
> Confirmed. Appointment booked: Dr. Sarah Chen, Cardiology, today 2:30 PM. Confirmation #A-78234. Flagged as urgent. Referral note: "Hypertensive urgency, BP 180/110, persistent headaches + blurred vision x2 weeks, lisinopril compliance to verify."

> "Patient looked up, triaged, and booked with a specialist. Three different backend systems accessed through MCP, orchestrated by Llama Stack, presented to the nurse in natural language. The application made three REST calls total. No MCP client code. No tool execution logic. That's what a middleware-first architecture looks like."

---

## Act 4: The Model-Agnostic Story (5 min)

### Set the Scene (2 min)

> "Everything you just saw was running on Qwen 3.6 27B -- a dense model from the Qwen family, served in FP8 precision via vLLM. But here's the key point about this architecture: the application doesn't know or care which model is behind Llama Stack."

> "In most architectures, switching from one model vendor to another is a project. Different tokenizer, different prompt format, maybe different API. Not here. In Llama Stack, you change the inference provider in the ConfigMap. The application never changes."

### Explain the Architecture (3 min)

**[SWITCH TO: Terminal or slides]**

**Show the Llama Stack ConfigMap:**
```yaml
providers:
  inference:
    - provider_id: qwen-vllm
      provider_type: remote::vllm
      config:
        base_url: http://qwen36-27b-predictor.triage-assistant.svc.cluster.local:8080/v1
```

> "To switch to a different model -- say Granite for IBM alignment, or Llama for Meta's latest -- you deploy a new InferenceService, update this URL in the ConfigMap, and restart Llama Stack. The application keeps calling the same endpoint. Zero code changes. Zero redeployments."

> "On our cluster we have 4 GPUs dedicated to model serving. But the architecture supports multiple models on larger clusters. Llama Stack can route to multiple inference providers. The platform team controls which model serves the application -- the AI safety committee decides, the ops team executes, the application team doesn't need to be involved."

> "This is what model-agnostic middleware gives you: zero vendor lock-in. Today it's Qwen. Tomorrow it could be Granite, Llama, Mistral, or whatever the next state-of-the-art model is. The application is decoupled from the model layer entirely."

---

## Act 5: The Full Middleware Story (3 min)

> "Let me step back and show you the big picture of what we've built."

**[SLIDE: Governance summary]**

> "In this architecture, the Quarkus application is intentionally thin. It knows one thing: where Llama Stack is, and what system prompt to use. Everything else -- which model to use, which tools the agent can access, how the agentic loop runs -- is governed by Llama Stack."

| What changes | Who does it | How | App impact |
|-------------|-------------|-----|------------|
| Swap AI model | Platform team | Update Llama Stack ConfigMap + restart | None |
| Add new tool (e.g., lab results MCP) | Platform team | Add connector in Llama Stack config | None (or add connector ID to tools list) |
| Remove tool access | Platform team | Remove connector | None |
| Change system prompt | App team | Update `application.properties` | Config-only restart |
| Change UI layout | App team | Update HTML/CSS | Code change |

> "Model governance AND tool governance are platform operations. The AI safety committee decides. The ops team executes. The application team doesn't need to be involved."

> "This is what enterprise AI middleware should look like. Not a thin proxy. Not a framework inside your app. A server-side orchestrator that handles inference, tools, and the agentic loop -- governed through Kubernetes-native resources."

---

## Act 6: Wrap-up and Key Takeaways (2 min)

**[SLIDE: Key Takeaways]**

1. **Data stays on-premise.** OpenShift AI hosts the models inside the hospital's infrastructure. Patient data never leaves the cluster -- not even during tool execution, because Llama Stack runs tools server-side.

2. **Llama Stack is full middleware, not just a gateway.** It routes models, discovers and executes MCP tools, and runs the agentic loop server-side. Both model switching and tool switching are platform operations.

3. **MCP servers make the agent extensible.** Three Quarkus MCP servers, each with just a few annotated Java methods. Adding a new capability means deploying a new MCP server and adding a connector in Llama Stack. No application changes.

4. **The application is intentionally thin.** The Quarkus chat UI makes one REST call per user message. No MCP client logic. No agentic loop. All orchestration is delegated to the middleware.

> "This architecture applies to any regulated industry -- financial services, government, defense -- anywhere you need data sovereignty, model governance, and enterprise integration. The stack is the same."

> "Thank you. Let's open it up for questions."

---

## Q&A Preparation: Likely Questions

**Q: Can the agent handle multiple patients simultaneously?**
A: Yes. Each request to `/v1/responses` is independent. The Quarkus app manages sessions via WebSocket. Llama Stack handles concurrent agentic loops. MCP servers are stateless.

**Q: How do you handle hallucinations in clinical recommendations?**
A: The MCP tools return structured data (triage rules, drug interaction tables), so the agent's recommendations are grounded in tool results, not hallucinated. In production you'd add Llama Stack safety shields and human-in-the-loop validation. The nurse is always the decision-maker.

**Q: What about latency? The agentic loop runs server-side -- is it slower?**
A: Server-side execution actually reduces latency because there's no network round-trip between client and MCP servers for each tool call. Llama Stack talks to MCP servers directly within the cluster. Qwen 3.6 27B completes a multi-tool interaction in 5-15 seconds with tensor parallelism across 4 GPUs.

**Q: Could we use multiple models?**
A: Absolutely. On a cluster with more GPUs, you could run a small 8B model for 80% of interactions (lookups, scheduling) and a powerful 32B+ model for clinical reasoning. Llama Stack's routing table handles this transparently. They don't even have to be from the same vendor.

**Q: What if Llama Stack goes down?**
A: The Operator manages replicas and health checks. In production you'd run multiple Llama Stack instances behind a Kubernetes Service. For the demo, we keep it simple.

**Q: Can I use this with models other than Mistral?**
A: Yes. Llama Stack OGX supports 50+ providers. Any model with an OpenAI-compatible API (vLLM, TGI, Ollama, cloud providers) can be a provider. We chose Qwen for its tool-calling quality, but Granite, Llama, Mistral, or any other model works. The application doesn't change.

**Q: How hard is it to connect to a real EHR system?**
A: The MCP server interface stays the same. Replace the in-memory data store with calls to FHIR/HL7 APIs. The `@Tool` method signatures don't change -- only the implementation.

**Q: Why not use LangChain4j in the Quarkus app with `@McpToolBox`?**
A: You could, and it would work. But then MCP tool management is in the application, not the middleware. You'd need a code change or config update in the app to add/remove tools. With Llama Stack as full middleware, tool governance is a platform operation -- the application stays untouched.

**Q: Can the agent access tools that aren't registered in Llama Stack?**
A: The app sends connector IDs in the `/v1/responses` request. Only connectors that are registered in Llama Stack config are available. This is a security feature: the platform team controls which tools the agent can use.
