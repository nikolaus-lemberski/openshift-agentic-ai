#!/usr/bin/env bash
set -euo pipefail

REGISTRY="quay.io"
USERNAME="nlembers"
PREFIX="agentic-ai-webinar-2026"
TAG="${1:-latest}"

SERVICES=(
  triage-assistant
  mcp-patient-records
  mcp-medical-knowledge
  mcp-scheduling
)

echo "Building container images (tag: ${TAG})"
echo ""

for SERVICE in "${SERVICES[@]}"; do
  IMAGE="${REGISTRY}/${USERNAME}/${PREFIX}-${SERVICE}:${TAG}"

  echo "==> ${SERVICE}"
  echo "    Image: ${IMAGE}"

  podman build -t "${IMAGE}" "./${SERVICE}"
  echo ""
done

echo "All images built successfully."
echo ""
echo "To push: ./tag_and_push.sh ${TAG}"
