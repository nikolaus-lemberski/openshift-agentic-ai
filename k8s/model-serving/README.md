# Model Serving - Phase 3

Deploy vLLM model serving with OCI/ModelCar storage on OpenShift AI. The model is internal-only (no external Route) -- accessed by Llama Stack within the same namespace.

## Hardware

- Single NVIDIA L4 GPU (24GB VRAM) on g6.8xlarge
- Model: **Mistral Small 3.1 24B Instruct (INT4/W4A16)** -- the largest validated model that fits on L4

## Prerequisites

- OpenShift 4.16+ with OpenShift AI 3.x installed
- GPU node available (NVIDIA L4 or better)
- `oc` CLI logged in as cluster-admin
- Pull secret for `registry.redhat.io` configured on the cluster

## Deployment

```bash
./k8s/model-serving/deploy.sh
```

Or manually:

```bash
# 1. Create namespace (shared across all components)
oc apply -f k8s/llamastack/namespace.yml

# 2. Deploy ServingRuntime and InferenceService
oc apply -f k8s/model-serving/serving-runtime.yml
oc apply -f k8s/model-serving/inferenceservice.yml

# 3. Wait for model to load (pulls OCI image + loads into GPU, may take several minutes)
oc wait --for=condition=Ready inferenceservice/mistral-small-3-1-24b \
  -n triage-assistant --timeout=600s
```

## Internal Service URL

The model is available within the `triage-assistant` namespace at:

```
http://mistral-small-3-1-24b-predictor:8080
```

Or using the full FQDN:

```
http://mistral-small-3-1-24b-predictor.triage-assistant.svc.cluster.local:8080
```

This is the URL Llama Stack (OGX) uses as its inference provider.

## Verification

Use a temporary pod to test from within the cluster:

```bash
# List models
oc run curl-test -n triage-assistant --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://mistral-small-3-1-24b-predictor:8080/v1/models

# Test chat completion
oc run curl-test -n triage-assistant --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal -- \
  curl -s http://mistral-small-3-1-24b-predictor:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"mistral-small-3-1-24b","messages":[{"role":"user","content":"Hello"}],"max_tokens":50}'
```

## Model Details

| Property | Value |
|----------|-------|
| Model | Mistral Small 3.1 24B Instruct (2503) |
| Quantization | W4A16 (4-bit weights, 16-bit activations) |
| VRAM usage | ~12GB weights + KV cache |
| Context window | 8192 tokens (configurable, up to 32K) |
| Tool calling | `--tool-call-parser=mistral` + `tool_chat_template_mistral3.jinja` |
| Eager mode | `--enforce-eager` (skips CUDA graph compilation for fast startup) |
| OCI image | `registry.redhat.io/rhelai1/modelcar-mistral-small-3-1-24b-instruct-2503-quantized-w4a16:1.5` |
| Namespace | `triage-assistant` |
| Internal URL | `http://mistral-small-3-1-24b-predictor:8080` |

## Alternative Models (also fit on L4 24GB)

| Model | OCI Image | Tool parser |
|-------|-----------|-------------|
| Granite 3.1 8B (FP16) | `oci://registry.redhat.io/rhelai1/modelcar-granite-3-1-8b-instruct:1.5` | `granite` |
| Qwen3 8B (FP8) | `oci://registry.redhat.io/rhelai1/modelcar-qwen3-8b-fp8-dynamic:1.5` | `hermes` |
| Phi-4 14B (INT4) | `oci://registry.redhat.io/rhelai1/modelcar-phi-4-quantized-w4a16:1.5` | `hermes` |
| Granite 3.3 8B | `oci://quay.io/redhat-ai-services/modelcar-catalog:granite-3.3-8b-instruct` | `granite` |

To switch models, update `storageUri` and `--tool-call-parser` in `inferenceservice.yml`.

## Notes

- **Single namespace** -- all components (model serving, Llama Stack, MCP servers, chat UI) live in `triage-assistant`.
- **Internal only** -- `serving.kserve.io/disableRouteCreation: "true"` prevents external Route creation.
- **No S3/MinIO required** -- OCI/ModelCar pulls the model directly as a container image layer.
- **No auth needed** -- traffic stays within the cluster network.
- **Single GPU constraint** -- only one model can be served at a time on this cluster.
- **First pull may be slow** -- the ModelCar image is large (~12GB). Subsequent starts are fast due to image caching.
