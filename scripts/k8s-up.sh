#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KUBECONFIG="${KUBECONFIG:-/tmp/stockflow-k8s-tools/kubeconfig}"
cluster="${STOCKFLOW_KIND_CLUSTER:-stockflow-lab}"
namespace="${STOCKFLOW_NAMESPACE:-stockflow}"
context="kind-$cluster"
for tool in docker kind kubectl helm; do command -v "$tool" >/dev/null || { echo "Missing $tool" >&2; exit 1; }; done
if ! kind get clusters | grep -qx "$cluster"; then
  kind create cluster --name "$cluster" --image kindest/node:v1.35.0 --config k8s/kind.yaml
fi
# Always address the explicitly named kind cluster, never the caller's current context.
k() { kubectl --context "$context" "$@"; }
k create namespace "$namespace" --dry-run=client -o yaml | k apply -f -
# Credentials stay outside Helm values and release storage.
# Resolve references from the same effective values used by the installation.
backend_secret=$(helm template stockflow ./helm/stockflow --namespace "$namespace" \
  -f ./helm/stockflow/values-local.yaml "$@" --show-only templates/backend.yaml | \
  k create --dry-run=client -f - -o jsonpath='{..secretRef.name}')
grafana_secret=$(helm template stockflow ./helm/stockflow --namespace "$namespace" \
  -f ./helm/stockflow/values-local.yaml "$@" --show-only templates/grafana.yaml | \
  k create --dry-run=client -f - -o jsonpath='{..secretKeyRef.name}')
k -n "$namespace" get secret "$backend_secret" >/dev/null
k -n "$namespace" get secret "$grafana_secret" >/dev/null
docker build -t stockflow-backend:stage5-v1 backend
docker build -t stockflow-frontend:stage5-v1 frontend
# Also load dependencies: no Docker Hub access is needed from cluster pods.
for image in postgres:17-alpine prom/prometheus:v3.14.0 grafana/grafana:13.2.2 prom/alertmanager:v0.34.1; do
  docker image inspect "$image" >/dev/null 2>&1 || docker pull "$image"
done
# Import only this host platform: Docker containerd stores may retain a multi-arch
# index without the other architectures' layers, which kind --all-platforms rejects.
platform="linux/$(docker info --format '{{.Architecture}}')"
[[ "$platform" != linux/x86_64 ]] || platform=linux/amd64
[[ "$platform" != linux/aarch64 ]] || platform=linux/arm64
docker save stockflow-backend:stage5-v1 stockflow-frontend:stage5-v1 postgres:17-alpine prom/prometheus:v3.14.0 grafana/grafana:13.2.2 prom/alertmanager:v0.34.1 | docker exec -i "$cluster-control-plane" ctr --namespace=k8s.io images import --platform "$platform" --digests -
helm template stockflow ./helm/stockflow --namespace "$namespace" -f ./helm/stockflow/values-local.yaml "$@" | k apply --dry-run=server -f -
helm upgrade --install stockflow ./helm/stockflow --kube-context "$context" \
  --namespace "$namespace" --create-namespace -f ./helm/stockflow/values-local.yaml \
  "$@" --wait --timeout 6m
k -n "$namespace" rollout status statefulset/database --timeout=180s
for deployment in backend frontend prometheus grafana alertmanager; do
  k -n "$namespace" rollout status "deployment/$deployment" --timeout=240s
done
k -n "$namespace" get pods,services,pvc
