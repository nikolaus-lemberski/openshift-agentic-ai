#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="triage-model-serving"
ISVC_NAME="mistral-small-3-1-24b"
TIMEOUT="${1:-600s}"

echo "=== Phase 3: Model Serving Deployment ==="
echo ""

# 1. Clean up old namespace if it exists
if oc get ns my-first-model &>/dev/null; then
  echo "[1/5] Deleting old 'my-first-model' namespace..."
  oc delete ns my-first-model --wait=false
  echo "      Namespace deletion initiated (will complete in background)"
else
  echo "[1/5] Namespace 'my-first-model' does not exist, skipping"
fi
echo ""

# 2. Create namespace
echo "[2/5] Creating namespace '${NAMESPACE}'..."
oc apply -f "${SCRIPT_DIR}/namespace.yml"
echo ""

# 3. Deploy ServingRuntime
echo "[3/5] Deploying ServingRuntime..."
oc apply -f "${SCRIPT_DIR}/serving-runtime.yml"
echo ""

# 4. Deploy InferenceService
echo "[4/5] Deploying InferenceService '${ISVC_NAME}'..."
oc apply -f "${SCRIPT_DIR}/inferenceservice.yml"
echo ""

# 5. Wait for model to be ready
echo "[5/5] Waiting for model to be ready (timeout: ${TIMEOUT})..."
echo "      (OCI image pull + model loading may take several minutes on first deploy)"
echo ""

if oc wait --for=condition=Ready "inferenceservice/${ISVC_NAME}" \
  -n "${NAMESPACE}" --timeout="${TIMEOUT}"; then

  ISVC_URL=$(oc get inferenceservice "${ISVC_NAME}" -n "${NAMESPACE}" \
    -o jsonpath='{.status.address.url}')

  echo ""
  echo "=== SUCCESS ==="
  echo ""
  echo "Model:    Mistral Small 3.1 24B Instruct (INT4/W4A16)"
  echo "Status:   Ready"
  echo "URL:      ${ISVC_URL}"
  echo ""
  echo "Test with:"
  echo "  oc run curl-test -n ${NAMESPACE} --rm -it --restart=Never \\"
  echo "    --image=registry.access.redhat.com/ubi9/ubi-minimal -- \\"
  echo "    curl -s ${ISVC_URL}/v1/models"
  echo ""
else
  echo ""
  echo "=== FAILED ==="
  echo ""
  echo "Model did not become ready within ${TIMEOUT}."
  echo ""
  echo "Debugging:"
  echo "  oc get pods -n ${NAMESPACE}"
  echo "  oc logs -n ${NAMESPACE} -l serving.kserve.io/inferenceservice=${ISVC_NAME} -c kserve-container --tail=50"
  echo "  oc get events -n ${NAMESPACE} --sort-by='.lastTimestamp'"
  echo ""
  exit 1
fi
