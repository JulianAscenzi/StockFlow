# StockFlow Helm Chart

Chart autónomo basado en los manifiestos Kubernetes validados. Una release por namespace; Secrets externos obligatorios. Instalación, values, imágenes kind, upgrades, rollback, persistencia y evidencia: [docs/HELM.md](../../docs/HELM.md).

```bash
helm upgrade --install stockflow ./helm/stockflow --namespace stockflow --create-namespace \
  -f ./helm/stockflow/values-local.yaml --wait --timeout 6m
```

Crear primero stockflow-secrets y grafana-secret en ese namespace; cargar las imágenes locales. No instalar sobre los recursos raw existentes. Uninstall preserva los cuatro PVCs y los Secrets externos; consultar la guía para reinstalación mediante existingClaim.
