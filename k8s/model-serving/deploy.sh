#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="triage-assistant"
ISVC_NAME="mistral-small-3-1-24b"
TIMEOUT="${1:-600s}"

echo "=== Phase 3: Model Serving Deployment ==="
echo ""

# 1. Create namespace (shared with Llama Stack and apps)
echo "[1/4] Creating namespace '${NAMESPACE}'..."
oc apply -f "${SCRIPT_DIR}/../llamastack/namespace.yml"
echo ""

# 2. Deploy ServingRuntime
echo "[2/4] Deploying ServingRuntime..."
oc apply -f "${SCRIPT_DIR}/serving-runtime.yml"
echo ""

# 3. Deploy InferenceService
echo "[3/4] Deploying InferenceService '${ISVC_NAME}'..."
oc apply -f "${SCRIPT_DIR}/inferenceservice.yml"
echo ""

# 4. Wait for model to be ready
echo "[4/4] Waiting for model to be ready (timeout: ${TIMEOUT})..."
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
