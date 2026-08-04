#!/usr/bin/env bash
set -euo pipefail

REGISTRY=quay.io
USERNAME=nlembers
PREFIX="agentic-ai-webinar-2026"
TAG="${1:-latest}"

SERVICES=(
  triage-assistant
  mcp-patient-records
  mcp-medical-knowledge
  mcp-scheduling
)

echo "Pushing images to ${REGISTRY}/${USERNAME}/${PREFIX}-* with tag '${TAG}'"
echo ""

for SERVICE in "${SERVICES[@]}"; do
  IMAGE="${REGISTRY}/${USERNAME}/${PREFIX}-${SERVICE}:${TAG}"

  echo "==> ${SERVICE}"
  echo "    Image: ${IMAGE}"

  podman push "${IMAGE}"
  echo ""
done

echo "All images pushed successfully."
