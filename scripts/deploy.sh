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

# ─── Phase 2: SeaweedFS (S3-compatible model storage) ────────────────────────

echo "=== Phase 2: SeaweedFS ==="
echo ""

echo "[1/6] Creating SeaweedFS S3 config secret..."
oc apply -f "${K8S_DIR}/seaweedfs/secret-s3-config.yml"

echo "[2/6] Creating SeaweedFS credentials secret..."
oc apply -f "${K8S_DIR}/seaweedfs/secret.yml"

echo "[3/6] Creating model-serving ServiceAccount..."
oc apply -f "${K8S_DIR}/seaweedfs/serviceaccount.yml"

echo "[4/6] Creating PersistentVolumeClaim..."
oc apply -f "${K8S_DIR}/seaweedfs/pvc.yml"

echo "[5/6] Deploying SeaweedFS..."
oc apply -f "${K8S_DIR}/seaweedfs/deployment.yml"

echo "[6/6] Creating SeaweedFS Service..."
oc apply -f "${K8S_DIR}/seaweedfs/service.yml"

echo ""
echo "Waiting for SeaweedFS to be ready..."
oc rollout status deployment/seaweedfs -n "${NAMESPACE}" --timeout=120s
echo ""

# ─── Phase 2b: HuggingFace Token ────────────────────────────────────────────

if [ -n "${HF_TOKEN:-}" ]; then
  echo "[HF] Creating HuggingFace token secret from \$HF_TOKEN..."
  envsubst < "${K8S_DIR}/seaweedfs/secret-hf-token.yml" | oc apply -f -
  echo ""
else
  echo "[HF] No \$HF_TOKEN set -- skipping HuggingFace token secret."
  echo "     (Not required: PromptGuard model is ungated.)"
  echo "     Set HF_TOKEN=hf_xxx for faster authenticated downloads."
  echo ""
fi

# ─── Phase 3: Model Pull (HuggingFace → SeaweedFS) ──────────────────────────

echo "=== Phase 3: Model Pull ==="
echo ""
echo "Launching Job to download model from HuggingFace into SeaweedFS..."
echo ""

oc delete job pull-qwen35-9b -n "${NAMESPACE}" --ignore-not-found
oc apply -f "${K8S_DIR}/seaweedfs/job-pull-model.yml"

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

# ─── Phase 5: OGX (Llama Stack) ────────────────────────────────────────────

echo "=== Phase 5: OGX (Llama Stack) ==="
echo ""

echo "[1/3] Applying OGX ConfigMap..."
oc apply -f "${K8S_DIR}/llamastack/configmap-stack-config.yml"

echo "[2/3] Deploying LlamaStackDistribution..."
oc apply -f "${K8S_DIR}/llamastack/llamastack-distribution.yml"

echo "[3/3] Creating LlamaStack Service..."
oc apply -f "${K8S_DIR}/llamastack/service.yml"

echo ""
echo "Waiting for OGX pod to be ready..."
oc wait --for=condition=Ready pod \
  -l app.kubernetes.io/instance=triage-llamastack \
  -n "${NAMESPACE}" --timeout=120s || {
  echo ""
  echo "WARNING: OGX pod did not become ready within 120s."
  echo "Check status: oc get pods -n ${NAMESPACE} -l app.kubernetes.io/instance=triage-llamastack"
  echo ""
}
echo ""

# ─── Phase 6: PromptGuard Model Pull (protectai/deberta-v3-base-prompt-injection-v2) ──

echo "=== Phase 6: PromptGuard Model Pull ==="
echo ""
echo "Launching Job to download PromptGuard model from HuggingFace into SeaweedFS..."
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

# ─── Phase 8: OpenShift AI Gateway Timeout ────────────────────────────────

echo "=== Phase 8: OpenShift AI Gateway Timeout ==="
echo ""
echo "Configuring route timeouts for LLM inference (default 30s is too low"
echo "for Qwen 3.5 thinking mode which can take 60-120s+)..."
echo ""

echo "[1/2] Annotating data-science-gateway route (300s timeout)..."
oc annotate route data-science-gateway \
  -n redhat-ods-applications \
  haproxy.router.openshift.io/timeout=300s \
  --overwrite

echo "[2/2] Annotating rhods-dashboard route (300s timeout)..."
oc annotate route rhods-dashboard \
  -n redhat-ods-applications \
  haproxy.router.openshift.io/timeout=300s \
  --overwrite

echo ""

# ─── Phase 9: GenAI Playground Token Budget ───────────────────────────────

echo "=== Phase 9: GenAI Playground Token Budget ==="
echo ""
echo "Increasing VLLM_MAX_TOKENS for the GenAI playground (Qwen 3.5 thinking"
echo "mode consumes many tokens on reasoning before producing visible output)..."
echo ""

if oc get llamastackdistribution lsd-genai-playground -n "${NAMESPACE}" &>/dev/null; then
  oc patch llamastackdistribution lsd-genai-playground -n "${NAMESPACE}" \
    --type='json' \
    -p='[{"op":"replace","path":"/spec/server/containerSpec/env","value":[{"name":"VLLM_TLS_VERIFY","value":"false"},{"name":"MILVUS_DB_PATH","value":"~/.llama/milvus.db"},{"name":"FMS_ORCHESTRATOR_URL","value":"http://localhost"},{"name":"VLLM_MAX_TOKENS","value":"8192"},{"name":"VLLM_API_TOKEN_1","value":"fake"},{"name":"LLAMA_STACK_CONFIG_DIR","value":"/opt/app-root/src/.llama/distributions/rh/"}]}]'
  echo "  Patched: VLLM_MAX_TOKENS 4096 -> 8192"
else
  echo "  GenAI playground LlamaStackDistribution not found (created via RHOAI UI)."
  echo "  Skipping -- apply manually after creating the playground connection."
fi
echo ""

# ─── Summary ────────────────────────────────────────────────────────────────

echo "============================================"
echo "  Infrastructure Deployment Complete"
echo "============================================"
echo ""
echo "Deployed:"
echo "  - Namespace:        ${NAMESPACE}"
echo "  - SeaweedFS:        seaweedfs.${NAMESPACE}.svc:8333 (S3 API)"
echo "  - Model:            ${ISVC_NAME} (vLLM)"
echo "  - OGX (Llama Stack): triage-llamastack.${NAMESPACE}.svc:8321 (safety provider pre-registered)"
echo "  - PromptGuard:      promptguard-service.${NAMESPACE}.svc:8080 (dormant until activated)"
echo "  - Route timeout:    300s on data-science-gateway and rhods-dashboard"
echo "  - GenAI playground: VLLM_MAX_TOKENS=8192 (if playground exists)"
echo ""
echo "Next steps:"
echo "  1. Deploy the MCP servers:"
echo "       oc apply -f k8s/apps/mcp-servers/"
echo "  2. Register MCP servers in OpenShift AI:"
echo "       oc apply -f k8s/openshift-ai/gen-ai-aa-mcp-servers.yml"
echo "  3. Deploy the triage assistant app:"
echo "       oc apply -f k8s/apps/triage-assistant/"
echo ""
echo "  4. To activate guardrails during the demo:"
echo "       oc set env deployment/triage-assistant -n ${NAMESPACE} GUARDRAIL_IDS=prompt-guard"
echo ""
