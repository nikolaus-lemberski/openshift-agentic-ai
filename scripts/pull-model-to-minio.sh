#!/usr/bin/env bash
set -euo pipefail

# Launches the model pull Job in OpenShift that downloads Qwen3.6-27B-FP8
# from HuggingFace directly into the MinIO bucket within the cluster.
#
# Prerequisites:
#   - MinIO must be running: oc apply -f k8s/minio/
#   - (Optional) HuggingFace token for gated models:
#     oc create secret generic hf-token --from-literal=token=hf_xxx -n triage-assistant

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
NAMESPACE="${NAMESPACE:-triage-assistant}"

echo "=== Qwen3.6-27B-FP8 Model Pull ==="
echo ""
echo "This launches a Job in OpenShift that downloads the model"
echo "from HuggingFace directly into MinIO (no local download needed)."
echo ""

# Delete previous job run if it exists
oc delete job pull-qwen36-27b -n "$NAMESPACE" --ignore-not-found

# Apply the ConfigMap + Job
echo "Applying pull-model Job..."
oc apply -f "$PROJECT_DIR/k8s/minio/job-pull-model.yml"

echo ""
echo "Job started. Monitor progress with:"
echo "  oc logs -f job/pull-qwen36-27b -n $NAMESPACE"
echo ""
echo "Check status with:"
echo "  oc get job pull-qwen36-27b -n $NAMESPACE"
