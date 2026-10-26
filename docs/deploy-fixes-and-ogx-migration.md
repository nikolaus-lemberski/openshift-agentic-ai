# Deploy Fixes & OGX Migration — Status Notes (2026-10-26)

Notes from debugging `scripts/deploy.sh` on the RHOAI 3.5.2 cluster. The script hung at
Phase 4 ("Waiting for model to be ready"). That turned out to be a chain of separate problems.

**Update (same day, second session):** verified end-to-end on a fresh RHOAI 3.5.2 cluster. See section 3.

---

## 1. Findings

### 1.1 SeaweedFS had no writable volumes (root cause of the hang)
- `k8s/seaweedfs/deployment.yml` used `-master.volumeSizeLimitMB=30000` with `-volume.max=0` (auto).
  SeaweedFS computes the slot count as free disk ÷ volume size. With a 25Gi PVC that is almost
  zero, so the `models` collection could not grow any volumes.
- Every S3 `PutObject` returned `InternalError` and the logs showed
  `No writable volumes ... Not enough data nodes found`.
- The model pull job failed after about 2 minutes, which left the bucket empty. The KServe
  storage-initializer then failed with `No model found in qwen3.5-9b`, so the InferenceService
  never became Ready.

### 1.2 `oc wait --for=condition=Complete` hangs on failed Jobs
- A Job with condition `Failed` never matches `Complete`, so the script waited out the full
  30-minute timeout. This is why the script looked hung instead of failing.

### 1.3 The model pull job was not idempotent
- Every run re-downloaded the model and re-uploaded 13 GB. SeaweedFS does not reclaim
  overwritten data until it vacuums, so the second run filled the 25Gi PVC to 100%. That
  marked the volumes read-only and **reverted the `tokenizer_config.json` vLLM patch**: the
  file was re-uploaded, then the job died before the patch step.

### 1.4 RHOAI 3.5: Llama Stack Operator was replaced by OGX
- In the DataScienceCluster, `spec.components.llamastackoperator` is deprecated and ignored.
  The new component is `spec.components.ogx`.
- OGX refuses to install while `llamastackoperator` is still `Managed`
  (operator log: `set it to Removed before enabling OGX`).
- The CRD changed: `LlamaStackDistribution` (`llamastack.io/v1alpha1`) became
  **`OGXServer` (`ogx.io/v1beta1`)** with a different spec:
  - `spec.server.distribution` → `spec.distribution`
  - `spec.server.userConfig.configMapName` → `spec.overrideConfig.{name,key}`
  - `spec.network.exposeRoute` → `spec.network.externalAccess.enabled`
  - The config ConfigMap must carry the label `ogx.io/watch: "true"`.
  - Distribution `rh-dev` is deprecated; use `rh` (same image).
- The operator now **creates the Service itself** (`triage-llamastack-service`, selector
  `app: ogx`). Our own `service.yml` selected `app: llama-stack` and would have broken it.

### 1.5 OGX removed the Safety API / shields
- The server crash-looped with `ValueError: API 'safety' does not exist`.
- Guardrails now work differently:
  - **Server:** `moderation_endpoint` on the `inline::builtin` responses provider. It takes
    any OpenAI-compatible `/v1/moderations` URL and fails closed: if the endpoint can't be
    reached, the request is blocked.
  - **Request:** `"guardrails": true` (a boolean) instead of a list of shield IDs.
- Our PromptGuard service already serves an OpenAI-compatible `POST /v1/moderations`,
  so it plugs in directly.

---

## 2. What was done

### On the cluster
| Change | State |
|---|---|
| SeaweedFS redeployed with 1GB volumes / `volume.max=30` | ✅ |
| SeaweedFS PVC expanded 25Gi → 40Gi (gp3-csi supports expansion) | ✅ |
| Garbage from the duplicate upload vacuumed; read-only volumes cleared by restart | ✅ |
| Model in `s3://models/qwen3.5-9b/` verified (all file sizes match); `.complete` marker written by hand | ✅ |
| `tokenizer_config.json` re-patched (pull job re-run in skip mode) | ✅ |
| InferenceService `qwen35-9b` Ready (vLLM on L4) | ✅ |
| DSC: `llamastackoperator: Removed`, `ogx: Managed` → `OGXReady=True` | ✅ |
| `OGXServer/triage-llamastack` running; `triage-model` + `qwen-vllm/qwen35-9b` registered | ✅ |
| `/v1/responses` smoke test through OGX → vLLM | ✅ |

### In the repo (uncommitted)
- `k8s/seaweedfs/deployment.yml`: `-volume.max=30`, `-master.volumeSizeLimitMB=1024`,
  `strategy: Recreate` (RWO PVC).
- `k8s/seaweedfs/pvc.yml`: 40Gi.
- `k8s/seaweedfs/job-pull-model.yml`: connects to S3 first and skips the download and upload
  if the `.complete` marker exists. The marker is written after a successful upload. The
  tokenizer patch step always runs, and it is idempotent.
- `k8s/llamastack/llamastack-distribution.yml` → **`k8s/llamastack/ogx-server.yml`**
  (`OGXServer`, distribution `rh`, `overrideConfig`, port 8321, external access).
- `k8s/llamastack/service.yml`: **deleted** (the operator manages the Service).
- `k8s/llamastack/configmap-stack-config.yml`:
  - added the `ogx.io/watch: "true"` label
  - `image_name: rh`
  - removed the `safety` API, the `prompt-guard` safety provider, and the `shields` entry
  - added `moderation_endpoint: http://promptguard-service:8080/v1/moderations` to the
    responses provider
- `scripts/deploy.sh`:
  - New **Phase 0**: patches the DSC (`llamastackoperator: Removed`, `ogx: Managed`) and
    waits for `crd/ogxservers.ogx.io`.
  - New `wait_for_job` helper that returns immediately when a Job fails. Used in Phase 3
    and Phase 6.
  - Phase 5 applies the ConfigMap and `ogx-server.yml` (no Service); the pod wait is now 300s.
  - Phase 9 targets `ogxserver lsd-genai-playground` and appends `VLLM_MAX_TOKENS=8192`
    via JSON patch (`/spec/workload/overrides/env/-`), with a warning on failure.
    **Untested:** the playground didn't exist.
  - The final hint now says `GUARDRAILS_ENABLED=true`.
- `triage-assistant` (guardrails as a boolean):
  - `ResponseRequest.guardrails`: `List<String>` → `Boolean`
  - `TriageService`: `@ConfigProperty triage.guardrails-enabled` (boolean). Sends `true`, or
    omits the field.
  - `application.properties`: `triage.guardrails-enabled=${GUARDRAILS_ENABLED:false}`
    (replaces `GUARDRAIL_IDS`)

---

## 3. End-to-end verification (fresh cluster)

`scripts/deploy.sh` ran end-to-end on a fresh cluster (exit 0, Phases 0–9). Phase 0 can
switch `llamastackoperator` → `ogx` in a single patch. Phase 9 skipped because no playground
exists yet, so it is still **untested**.

Then the MCP servers, the RHOAI MCP ConfigMap, and the app (new image) were deployed, and the
scripted demo ran through the chat UI WebSocket:
- Interactions 1–4 (Maria Garcia lookup → ESI 2 triage → cardiology slots → booking with
  Dr. Chen): ✅
- Act 8 without guardrails: the attack dumps all patient records (as scripted). ✅
- `GUARDRAILS_ENABLED=true`: the attack is blocked (`flagged for: injection`), and a fresh
  Maria Garcia intake passes normally. ✅

### 3.1 New findings

**Thinking text leaked into answers.** Qwen 3.5's chat template opens `<think>` in the
prompt, so the output is `reasoning…</think>answer`. The old regex only removed the bare
closing tag. Fixed in `TriageService.THINK_PATTERN`, which now strips everything up to the
last `</think>`.

**OGX output guardrails moderate reasoning text → false positives.** OGX checks the
content *and* the reasoning every 200 chars (`streaming.py`, "reasoning text is included in
moderation checks"). There is no input-only option, and the moderation payload is a bare
`{"input": text}`. DeBERTa flagged self-correcting reasoning ("Wait, I notice…") as injection
(0.97–0.998), which blocked every legitimate request. Final answers score ≈0.
→ **Fix:** vLLM `--default-chat-template-kwargs={"enable_thinking": false}` in
`k8s/model-serving/serving-runtime.yml`. Side effect: faster responses, and the demo quality
was unchanged in testing. The 300s route timeouts (Phase 8) are harmless and kept.

**OGX bug: guardrails + `previous_response_id` → 400.** The input check stringifies the full
history and fails on MCP tool output (`{"detail":"Unsupported content type: <class 'list'>"}`).
The app's 400-retry fallback (drops `previous_response_id`) is **still needed**. The
consequence: with guardrails on, follow-up turns lose context. The script only does
single-turn requests after activation, so the demo is unaffected.

**GPU slots full: ServingRuntime changes deadlock the rollout.** The node has 4 allocatable
GPU slots. Two were taken by preinstalled sample models (`granite-2b`, `tinyllama` in namespace
`ai-models`), plus PromptGuard (requests a full GPU) and Qwen. The new predictor pod stayed
`Pending` until the old one was deleted by hand. `ai-models` has since been deleted, so 2 slots
are free and rolling updates work again. (Optional: run PromptGuard on CPU to free another slot.)

## 4. Remaining

1. Phase 9 (playground `VLLM_MAX_TOKENS` patch) is untested. Run it after creating the
   playground in the RHOAI UI. With thinking off, the patch may no longer be needed.
2. Rebuild/push the triage-assistant image if desired. The only change since the deployed
   image is a code comment.
3. Commit.

## Useful commands
```bash
oc get ogxserver -n triage-assistant
oc logs -l app.kubernetes.io/instance=triage-llamastack -n triage-assistant --tail=50
oc get datasciencecluster default-dsc -o jsonpath='{.status.conditions[?(@.type=="OGXReady")]}'
oc exec deploy/seaweedfs -n triage-assistant -- df -h /data
echo "fs.ls -l /buckets/models/qwen3.5-9b" | oc exec -i deploy/seaweedfs -n triage-assistant -- weed shell -master=localhost:9333
```
