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

COMPOSE_PROJECT="agentic-ai-webinar-2026"

echo "Tagging and pushing images to ${REGISTRY}/${USERNAME}/${PREFIX}-* with tag '${TAG}'"
echo ""

for SERVICE in "${SERVICES[@]}"; do
  LOCAL_IMAGE="localhost/${COMPOSE_PROJECT}_${SERVICE}"
  REMOTE_IMAGE="${REGISTRY}/${USERNAME}/${PREFIX}-${SERVICE}:${TAG}"

  echo "==> ${SERVICE}"
  echo "    Local:  ${LOCAL_IMAGE}"
  echo "    Remote: ${REMOTE_IMAGE}"

  podman tag "${LOCAL_IMAGE}" "${REMOTE_IMAGE}"
  podman push "${REMOTE_IMAGE}"
  echo ""
done

echo "All images pushed successfully."
