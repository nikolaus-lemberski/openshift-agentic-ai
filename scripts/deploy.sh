#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
K8S_DIR="$PROJECT_DIR/k8s"
NAMESPACE="${NAMESPACE:-triage-assistant}"
ISVC_NAME="qwen35-9b"
MODEL_TIMEOUT="${MODEL_TIMEOUT:-600s}"

echo "============================================"
echo "  Triage Assistant - Infrastructure Deploy"
echo "============================================"
echo ""
echo "Namespace:     ${NAMESPACE}"
echo "Model timeout: ${MODEL_TIMEOUT}"
echo ""

# ─── Phase 1: Namespace ─────────────────────────────────────────────────────

echo "=== Phase 1: Namespace ==="
echo ""
echo "[1/1] Creating namespace '${NAMESPACE}'..."
oc apply -f "${K8S_DIR}/namespace.yml"
echo ""

# ─── Phase 2: MinIO (S3-compatible model storage) ───────────────────────────

echo "=== Phase 2: MinIO ==="
echo ""

echo "[1/5] Creating MinIO credentials secret..."
oc apply -f "${K8S_DIR}/minio/secret.yml"

echo "[2/5] Creating model-serving ServiceAccount..."
oc apply -f "${K8S_DIR}/minio/serviceaccount.yml"

echo "[3/5] Creating PersistentVolumeClaim..."
oc apply -f "${K8S_DIR}/minio/pvc.yml"

echo "[4/5] Deploying MinIO..."
oc apply -f "${K8S_DIR}/minio/deployment.yml"

echo "[5/5] Creating MinIO Service..."
oc apply -f "${K8S_DIR}/minio/service.yml"

echo ""
echo "Waiting for MinIO to be ready..."
oc rollout status deployment/minio -n "${NAMESPACE}" --timeout=120s
echo ""

# ─── Phase 2b: HuggingFace Token ────────────────────────────────────────────

if [ -n "${HF_TOKEN:-}" ]; then
  echo "[HF] Creating HuggingFace token secret from \$HF_TOKEN..."
  envsubst < "${K8S_DIR}/minio/secret-hf-token.yml" | oc apply -f -
  echo ""
else
  echo "[HF] No \$HF_TOKEN set -- skipping HuggingFace token secret."
  echo "     (Not required: PromptGuard model is ungated.)"
  echo "     Set HF_TOKEN=hf_xxx for faster authenticated downloads."
  echo ""
fi

# ─── Phase 3: Model Pull (HuggingFace → MinIO) ─────────────────────────────

echo "=== Phase 3: Model Pull ==="
echo ""
echo "Launching Job to download model from HuggingFace into MinIO..."
echo ""

oc delete job pull-qwen35-9b -n "${NAMESPACE}" --ignore-not-found
oc apply -f "${K8S_DIR}/minio/job-pull-model.yml"

echo ""
echo "Waiting for model pull Job to complete (this may take 10-30 minutes)..."
oc wait --for=condition=Complete "job/pull-qwen35-9b" \
  -n "${NAMESPACE}" --timeout=1800s || {
  echo ""
  echo "WARNING: Model pull job did not complete within 30 minutes."
  echo "Check status: oc logs -f job/pull-qwen35-9b -n ${NAMESPACE}"
  echo "Continuing with deployment (model serving will wait for S3 data)..."
  echo ""
}
echo ""

# ─── Phase 4: Model Serving (vLLM on OpenShift AI) ─────────────────────────

echo "=== Phase 4: Model Serving ==="
echo ""

echo "[1/3] Deploying ServingRuntime..."
oc apply -f "${K8S_DIR}/model-serving/serving-runtime.yml"

echo "[2/3] Deploying InferenceService '${ISVC_NAME}'..."
oc apply -f "${K8S_DIR}/model-serving/inferenceservice.yml"

echo "[3/3] Waiting for model to be ready (timeout: ${MODEL_TIMEOUT})..."
echo "      (S3 model download + vLLM loading may take several minutes)"
echo ""

if oc wait --for=condition=Ready "inferenceservice/${ISVC_NAME}" \
  -n "${NAMESPACE}" --timeout="${MODEL_TIMEOUT}"; then

  ISVC_URL=$(oc get inferenceservice "${ISVC_NAME}" -n "${NAMESPACE}" \
    -o jsonpath='{.status.address.url}')

  echo ""
  echo "Model ready: ${ISVC_URL}"
  echo ""
else
  echo ""
  echo "WARNING: Model did not become ready within ${MODEL_TIMEOUT}."
  echo "Debugging:"
  echo "  oc get pods -n ${NAMESPACE}"
  echo "  oc logs -n ${NAMESPACE} -l serving.kserve.io/inferenceservice=${ISVC_NAME} -c kserve-container --tail=50"
  echo "Continuing with Llama Stack deployment..."
  echo ""
fi

# ─── Phase 5: Llama Stack ──────────────────────────────────────────────────

echo "=== Phase 5: Llama Stack ==="
echo ""

echo "[1/2] Applying Llama Stack ConfigMap..."
oc apply -f "${K8S_DIR}/llamastack/configmap-stack-config.yml"

echo "[2/2] Deploying LlamaStackDistribution..."
oc apply -f "${K8S_DIR}/llamastack/llamastack-distribution.yml"

echo ""
echo "Waiting for Llama Stack pod to be ready..."
oc wait --for=condition=Ready pod \
  -l app.kubernetes.io/instance=triage-llamastack \
  -n "${NAMESPACE}" --timeout=120s || {
  echo ""
  echo "WARNING: Llama Stack pod did not become ready within 120s."
  echo "Check status: oc get pods -n ${NAMESPACE} -l app.kubernetes.io/instance=triage-llamastack"
  echo ""
}
echo ""

# ─── Phase 6: PromptGuard Model Pull (protectai/deberta-v3-base-prompt-injection-v2) ──

echo "=== Phase 6: PromptGuard Model Pull ==="
echo ""
echo "Launching Job to download PromptGuard model from HuggingFace into MinIO..."
echo "(protectai/deberta-v3-base-prompt-injection-v2 -- ungated, Apache 2.0)"
echo ""

oc delete job pull-promptguard -n "${NAMESPACE}" --ignore-not-found
oc apply -f "${K8S_DIR}/promptguard/job-pull-promptguard.yml"

echo ""
echo "Waiting for PromptGuard model pull Job to complete..."
oc wait --for=condition=Complete "job/pull-promptguard" \
  -n "${NAMESPACE}" --timeout=600s || {
  echo ""
  echo "WARNING: PromptGuard model pull job did not complete within 10 minutes."
  echo "  Logs: oc logs -f job/pull-promptguard -n ${NAMESPACE}"
  echo "Continuing with deployment..."
  echo ""
}
echo ""

# ─── Phase 7: PromptGuard Service ────────────────────────────────────────

echo "=== Phase 7: PromptGuard Service ==="
echo ""

echo "[1/3] Creating PromptGuard code ConfigMap from source..."
oc create configmap promptguard-code \
  --from-file=server.py="${PROJECT_DIR}/promptguard-service/src/server.py" \
  -n "${NAMESPACE}" --dry-run=client -o yaml | oc apply -f -

echo "[2/3] Deploying PromptGuard Service..."
oc apply -f "${K8S_DIR}/promptguard/deployment.yml"

echo "[3/3] Creating PromptGuard Service..."
oc apply -f "${K8S_DIR}/promptguard/service.yml"

echo ""
echo "Waiting for PromptGuard pod to be ready..."
oc rollout status deployment/promptguard-service -n "${NAMESPACE}" --timeout=300s || {
  echo ""
  echo "WARNING: PromptGuard service did not become ready within 5 minutes."
  echo "Check status: oc get pods -n ${NAMESPACE} -l app=promptguard-service"
  echo ""
}
echo ""

# ─── Summary ────────────────────────────────────────────────────────────────

echo "============================================"
echo "  Infrastructure Deployment Complete"
echo "============================================"
echo ""
echo "Deployed:"
echo "  - Namespace:        ${NAMESPACE}"
echo "  - MinIO:            minio.${NAMESPACE}.svc:9000"
echo "  - Model:            ${ISVC_NAME} (vLLM)"
echo "  - Llama Stack:      triage-llamastack.${NAMESPACE}.svc:8321"
echo "  - PromptGuard:      promptguard-service.${NAMESPACE}.svc:8080"
echo ""
echo "Next steps:"
echo "  1. Deploy the apps (MCP servers + Chat UI):"
echo "       oc apply -f k8s/apps/"
echo ""
echo "  2. To enable guardrails (after demonstrating without):"
echo "       oc apply -f k8s/llamastack/configmap-stack-config-guardrails.yml"
echo "       oc delete pod -n ${NAMESPACE} -l app.kubernetes.io/instance=triage-llamastack"
echo "       oc set env deployment/triage-assistant -n ${NAMESPACE} GUARDRAIL_IDS=prompt-guard"
echo ""
