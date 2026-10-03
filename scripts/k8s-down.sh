#!/usr/bin/env bash
set -euo pipefail
# Deleting the cluster destroys its laboratory PVCs. Never invoked automatically.
if [[ "${1:-}" != --delete-lab-data ]]; then
  echo 'Use --delete-lab-data to explicitly delete stockflow-lab and its PVC data.' >&2
  exit 1
fi
kind delete cluster --name stockflow-lab
