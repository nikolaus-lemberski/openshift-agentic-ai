# Model Serving - Qwen 3.6 27B (FP8)

Deploy vLLM model serving with S3/MinIO storage on OpenShift AI. The model is internal-only (no external Route) -- accessed by Llama Stack within the same namespace.

## Hardware

- 2x NVIDIA L4 GPUs (24GB VRAM each) on g6.12xlarge
- Model: **Qwen 3.6 27B (FP8)** with tensor-parallel-size=2
- Dense 27.8B parameter model, hybrid attention (64 layers)

## Prerequisites

- OpenShift 4.16+ with OpenShift AI 3.x installed
- GPU node with at least 2x NVIDIA L4 (or better)
- MinIO deployed and model uploaded (see below)
- `oc` CLI logged in as cluster-admin

## Deployment

```bash
# 1. Deploy MinIO
oc apply -f k8s/minio/

# 2. Pull model into MinIO (runs as a Job in-cluster)
./scripts/pull-model-to-minio.sh
oc logs -f job/pull-qwen36-27b -n triage-assistant

# 3. Deploy ServingRuntime and InferenceService
oc apply -f k8s/model-serving/serving-runtime.yml
oc apply -f k8s/model-serving/inferenceservice.yml

# 4. Wait for model to load
oc wait --for=condition=Ready inferenceservice/qwen36-27b \
  -n triage-assistant --timeout=600s
```

## Internal Service URL

The model is available within the `triage-assistant` namespace at:

```
http://qwen36-27b-predictor:8080
```

Or using the full FQDN:

```
http://qwen36-27b-predictor.triage-assistant.svc.cluster.local:8080
```

This is the URL Llama Stack (OGX) uses as its inference provider.

## Verification

Use a temporary pod to test from within the cluster:

```bash
# List models
oc run curl-test -n triage-assistant --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://qwen36-27b-predictor:8080/v1/models

# Test chat completion
oc run curl-test -n triage-assistant --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://qwen36-27b-predictor:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen36-27b","messages":[{"role":"user","content":"Hello"}],"max_tokens":50}'
```

## Model Details

| Property | Value |
|----------|-------|
| Model | Qwen 3.6 27B (FP8) |
| Parameters | 27.8B dense (all active) |
| Quantization | FP8 (~29GB weights) |
| VRAM usage | ~14.5GB weights per GPU + KV cache |
| Context window | 32768 tokens (native supports up to 262K) |
| Tool calling | `--tool-call-parser=hermes` + `--enable-auto-tool-choice` |
| Tensor parallelism | 2 (across 2x L4 GPUs) |
| Eager mode | `--enforce-eager` (skips CUDA graph compilation) |
| Storage | MinIO S3: `s3://models/qwen3.6-27b-fp8` |
| Namespace | `triage-assistant` |
| Internal URL | `http://qwen36-27b-predictor:8080` |

## Notes

- **S3/MinIO storage** -- model weights are served from a MinIO bucket within the cluster.
- **Tensor parallel** -- the model is split across 2 GPUs; the serving runtime sets `--tensor-parallel-size=2`.
- **Single namespace** -- all components (MinIO, model serving, Llama Stack, MCP servers, chat UI) live in `triage-assistant`.
- **Internal only** -- `serving.kserve.io/disableRouteCreation: "true"` prevents external Route creation.
- **No auth needed** -- traffic stays within the cluster network.
- **2 of 4 GPUs used** -- leaves 2 L4 GPUs available for other workloads.
