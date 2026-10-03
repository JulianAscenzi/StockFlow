#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export KUBECONFIG="${KUBECONFIG:-/tmp/stockflow-k8s-tools/kubeconfig}"
cluster=stockflow-lab
for tool in docker kind kubectl; do command -v "$tool" >/dev/null || { echo "Missing $tool" >&2; exit 1; }; done
if ! kind get clusters | grep -qx "$cluster"; then
  kind create cluster --name "$cluster" --image kindest/node:v1.35.0 --config k8s/kind.yaml
fi
# Always address this dedicated cluster, never the caller's current context.
k() { kubectl --context kind-stockflow-lab "$@"; }
k apply -f k8s/namespace.yaml
k apply -f k8s/config.yaml
if ! k -n stockflow get secret stockflow-secrets >/dev/null 2>&1; then
  echo 'Create stockflow-secrets and grafana-secret first; see docs/KUBERNETES.md.' >&2
  exit 1
fi
k -n stockflow get secret grafana-secret >/dev/null
# ConfigMaps reuse the canonical Compose rules/dashboard sources without copies.
k -n stockflow create configmap prometheus-config --from-file=prometheus.yml=k8s/observability/prometheus.yml --dry-run=client -o yaml | k apply -f -
k -n stockflow create configmap prometheus-rules --from-file=monitoring/prometheus/rules --dry-run=client -o yaml | k apply -f -
k -n stockflow create configmap alertmanager-config --from-file=monitoring/alertmanager/alertmanager.yml --dry-run=client -o yaml | k apply -f -
k -n stockflow create configmap grafana-datasources --from-file=monitoring/grafana/provisioning/datasources --dry-run=client -o yaml | k apply -f -
k -n stockflow create configmap grafana-providers --from-file=monitoring/grafana/provisioning/dashboards --dry-run=client -o yaml | k apply -f -
k -n stockflow create configmap grafana-dashboards --from-file=monitoring/grafana/dashboards --dry-run=client -o yaml | k apply -f -
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
for directory in database backend frontend observability; do
  # prometheus.yml is application config, not a Kubernetes resource.
  for manifest in "k8s/$directory/"*.yaml; do
    k apply --dry-run=server -f "$manifest"
    k apply -f "$manifest"
  done
done
k -n stockflow rollout status statefulset/database --timeout=180s
for deployment in backend frontend prometheus grafana alertmanager; do
  k -n stockflow rollout status "deployment/$deployment" --timeout=240s
done
k -n stockflow get pods,services,pvc
