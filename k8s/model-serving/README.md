# Model Serving - Qwen 3.5 9B

Deploy vLLM model serving with S3/SeaweedFS storage on OpenShift AI. The model is internal-only (no external Route) -- accessed by Llama Stack within the same namespace.

## Hardware

- 1x NVIDIA GPU on g6.12xlarge
- Model: **Qwen 3.5 9B FP8** (quantized) with tensor-parallel-size=1

## Prerequisites

- OpenShift 4.16+ with OpenShift AI 3.x installed
- GPU node with at least 1x NVIDIA GPU
- SeaweedFS deployed and model uploaded (see below)
- `oc` CLI logged in as cluster-admin

## Deployment

```bash
# 1. Deploy SeaweedFS
oc apply -f k8s/seaweedfs/

# 2. Pull model into SeaweedFS (runs as a Job in-cluster)
./scripts/pull-model.sh
oc logs -f job/pull-qwen35-9b -n triage-assistant

# 3. Deploy ServingRuntime and InferenceService
oc apply -f k8s/model-serving/serving-runtime.yml
oc apply -f k8s/model-serving/inferenceservice.yml

# 4. Wait for model to load
oc wait --for=condition=Ready inferenceservice/qwen35-9b \
  -n triage-assistant --timeout=600s
```

## Internal Service URL

The model is available within the `triage-assistant` namespace at:

```
http://qwen35-9b-predictor:8080
```

Or using the full FQDN:

```
http://qwen35-9b-predictor.triage-assistant.svc.cluster.local:8080
```

This is the URL Llama Stack (OGX) uses as its inference provider.

## Verification

Use a temporary pod to test from within the cluster:

```bash
# List models
oc run curl-test -n triage-assistant --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://qwen35-9b-predictor:8080/v1/models

# Test chat completion
oc run curl-test -n triage-assistant --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://qwen35-9b-predictor:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen35-9b","messages":[{"role":"user","content":"Hello"}],"max_tokens":50}'
```

## Model Details

| Property | Value |
|----------|-------|
| Model | Qwen 3.5 9B |
| Source | HuggingFace `Qwen/Qwen3.5-9B` |
| dtype | auto |
| Context window | 16384 tokens (`--max-model-len=16384`) |
| Tool calling | `--tool-call-parser=qwen3_xml` + `--enable-auto-tool-choice` |
| Thinking mode | Disabled (`--default-chat-template-kwargs={"enable_thinking":false}`) |
| Additional flags | `--language-model-only`, `--gpu-memory-utilization=0.95` |
| Tensor parallelism | 1 (single GPU) |
| Shared memory | 12Gi |
| Storage | SeaweedFS S3: `s3://models/qwen3.5-9b` |
| Namespace | `triage-assistant` |
| Internal URL | `http://qwen35-9b-predictor:8080` |

## Notes

- **S3/SeaweedFS storage** -- model weights are pulled from HuggingFace via a Kubernetes Job and stored in a SeaweedFS bucket within the cluster.
- **Single GPU** -- the FP8-quantized model fits on one GPU; the serving runtime sets `--tensor-parallel-size=1`.
- **Single namespace** -- all components (SeaweedFS, model serving, Llama Stack, MCP servers, chat UI) live in `triage-assistant`.
- **Internal only** -- `serving.kserve.io/disableRouteCreation: "true"` prevents external Route creation.
- **No auth needed** -- traffic stays within the cluster network.
