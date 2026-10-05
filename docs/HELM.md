# Helm — Etapa 6

`helm/stockflow` empaqueta la infraestructura Kubernetes validada en Etapa 5: cinco Deployments, PostgreSQL 17 StatefulSet, seis Services internos, siete ConfigMaps, RBAC namespaced, PDB y persistencia. No tiene dependencias de charts, operadores ni recursos cluster-scoped. Helm es el flujo recomendado; `k8s/` permanece como referencia educativa (`raw manifests/reference`) y `scripts/k8s-up-raw.sh` reproduce el flujo anterior. No aplicar ambos gestores sobre el mismo namespace.

## Parámetros y contrato

`values.yaml` conserva imágenes, dos réplicas backend, recursos, probes, puertos 8080/9091, terminación de 45s, PDB minAvailable=1 y tamaños de almacenamiento validados. Sólo se parametrizan decisiones operativas útiles: imágenes, recursos, almacenamiento, referencias a Secrets y configuración backend. Reglas, seguridad, retención, networking interno y estrategia de rollout permanecen explícitos. `values.schema.json` rechaza cantidades/puertos inválidos y un grace period inferior a la fase Boot de 30s.

`values-local.yaml` identifica las imágenes locales del laboratorio y su pull policy. Actualmente los defaults usan las mismas imágenes funcionales: el flujo local no requiere registry. El pipeline GHCR validado en Etapa 7 permite overrides de repository/tag/digest; ver [CONTAINER_REGISTRY](CONTAINER_REGISTRY.md). Los overrides permiten cambiar esas referencias sin editar templates.

El namespace viene de `.Release.Namespace`: usar `--namespace … --create-namespace`. El Chart no crea Namespace. Los nombres de recursos conservan `backend`, `frontend`, `database`, etc. para mantener DNS, provisioning y herramientas existentes. **Una release StockFlow por namespace**; para otra instalación usar otro namespace. `app` permanece para los experimentos previos; selectors agregan `app.kubernetes.io/instance`. Prometheus filtra namespace, release, app=backend, puerto management y fase Running; no filtra readiness. Sólo tiene get/list/watch de pods en ese namespace. Management no se publica mediante Service.

`Chart.yaml`: apiVersion=v2, type=application, version=0.2.0 es la versión del paquete y sus templates; appVersion=0.0.1-SNAPSHOT identifica la versión actual del backend Maven. Son ciclos distintos: una modificación del Chart no implica una nueva aplicación. Los tags de imagen son referencias explícitas en values; appVersion no los reemplaza.

Backend/frontend aceptan `image.digest` vacío por defecto; un digest sha256 válido produce repository@digest y tiene prioridad sobre tag. Limpiarlo explícitamente para volver a tags. `global.imagePullSecrets` acepta nombres de Secrets externos existentes y se aplica a todos los workloads; por defecto se omite. No contiene credenciales. Los defaults kind y valores funcionales se conservan.

## Fuente única de observabilidad

Los archivos canónicos compartidos viven en `helm/stockflow/files/monitoring/`: 50 recording rules, seis alertas, Alertmanager, ambos dashboards y provisioning. Compose monta esos mismos archivos; Helm los incorpora con `.Files.Glob().AsConfig`. Los archivos se trasladaron sin modificar contenido. No hay copias generadas, symlinks externos ni pasos previos de sincronización; `helm package` produce un paquete autónomo.

La configuración de scraping Compose sigue en `monitoring/prometheus/prometheus.yml`; la de descubrimiento Kubernetes es `helm/stockflow/files/prometheus-kubernetes.yml`. Esta última usa `tpl` únicamente para namespace/release. Las reglas y dashboards **no** pasan por `tpl`: las expresiones `{{ $labels… }}` de Prometheus deben conservarse literales. `k8s/observability/prometheus.yml` sigue como ejemplo estático de la Etapa 5.

Los workloads incluyen checksum de ConfigMaps para que cambios de configuración provoquen su reinicio mediante Helm. Backend mantiene RollingUpdate maxUnavailable=0/maxSurge=1/minReadySeconds=5; observabilidad conserva Recreate y puede interrumpirse al reiniciar. La observabilidad no es HA. Un Secret externo no modifica el checksum: después de una rotación compatible debe reiniciarse la carga deliberadamente. Helm rollback no rota credenciales.

## Preparación local y Secrets

Requisitos: Docker, kind 0.31.0, kubectl 1.35.0 y Helm 3 (validado con 3.19.0). Mantener kubeconfig dedicado y contexto explícito. La creación del cluster y de Secrets se describe en [KUBERNETES](KUBERNETES.md#secrets). Para una instalación nueva:

```bash
export KUBECONFIG=/tmp/stockflow-k8s-tools/kubeconfig
kind create cluster --name stockflow-lab --image kindest/node:v1.35.0 --config k8s/kind.yaml
kubectl --context kind-stockflow-lab create namespace stockflow
# Archivos privados, chmod 600, fuera del repositorio; ver guía de generación.
kubectl --context kind-stockflow-lab -n stockflow create secret generic stockflow-secrets \
  --from-env-file=/tmp/stockflow-k8s-tools/secrets.env
kubectl --context kind-stockflow-lab -n stockflow create secret generic grafana-secret \
  --from-env-file=/tmp/stockflow-k8s-tools/grafana.env
```

`existingSecret` exige POSTGRES_PASSWORD, APP_ADMIN_PASSWORD y APP_JWT_SECRET; `grafana.existingSecret` exige password. Los nombres son configurables, las claves conservan el contrato actual. El Chart no crea Secrets ni lee sus valores con `lookup`. No pasar contraseñas por `--set`, archivos values, argumentos o templates: Helm guarda valores y manifiestos en su información de release (normalmente Secrets Kubernetes), también conserva revisiones y el contenido empaquetado del Chart. Sólo se almacenan aquí referencias, nunca credenciales. Secret Kubernetes no implica cifrado automático. No regenerar contraseñas sobre PostgreSQL/Grafana ya inicializados: variables de bootstrap no rotan cuentas persistidas.

## Imágenes e instalación

```bash
docker build -t stockflow-backend:stage5-v1 backend
docker build -t stockflow-frontend:stage5-v1 frontend
kind load docker-image --name stockflow-lab stockflow-backend:stage5-v1 stockflow-frontend:stage5-v1
# También deben estar disponibles postgres:17-alpine, prom/prometheus:v3.14.0,
# grafana/grafana:13.2.2 y prom/alertmanager:v0.34.1.
helm upgrade --install stockflow ./helm/stockflow \
  --kube-context kind-stockflow-lab --namespace stockflow --create-namespace \
  -f ./helm/stockflow/values-local.yaml --wait --timeout 6m
kubectl --context kind-stockflow-lab -n stockflow rollout status deployment/backend --timeout=240s
```

`./scripts/k8s-up.sh` comprueba herramientas/Secrets, crea el cluster si falta, construye y carga imágenes, valida por server dry-run y ejecuta el mismo Helm. No oculta fallos: usa set -euo pipefail. `STOCKFLOW_KIND_CLUSTER` y `STOCKFLOW_NAMESPACE` permiten un laboratorio aislado; defaults stockflow-lab/stockflow. Acepta argumentos Helm comunes a template/upgrade (`-f` y `--set`), útiles para existingClaim al reinstalar. Importa sólo la arquitectura host con `docker save | ctr images import --platform` porque kind load puede rechazar índices multi-arquitectura con capas ausentes en Docker containerd. Es una alternativa al comando kind load, sin registry. Las imágenes StockFlow no deben mutar tags ya desplegados.

Si `stockflow-lab/stockflow` ya contiene la Etapa 5, **no ejecutar install ni usar take-ownership automáticamente**: Helm no posee esos recursos y los selectors originales son inmutables. Mantener ese laboratorio como referencia y probar en cluster/namespace nuevo. Una migración de datos existente necesita respaldo y un plan explícito; esta etapa no elimina ni adopta sus recursos.

La comprobación de Secrets del script obtiene sus nombres del render con los mismos overrides de instalación: también admite `--set existingSecret=…` y `--set grafana.existingSecret=…`. No consulta ni incorpora sus valores al Chart.

## Upgrade y rollback

```bash
helm upgrade stockflow ./helm/stockflow --kube-context kind-stockflow-lab -n stockflow \
  -f ./helm/stockflow/values-local.yaml --set backend.replicaCount=3 --wait --timeout 6m
kubectl --context kind-stockflow-lab -n stockflow get pods -l app=backend
# Prometheus /api/v1/targets: tres targets stockflow UP.
helm upgrade stockflow ./helm/stockflow --kube-context kind-stockflow-lab -n stockflow \
  -f ./helm/stockflow/values-local.yaml --set backend.replicaCount=2 --wait --timeout 6m
# Imagen funcional equivalente, diferenciada únicamente por metadata OCI:
docker build --label org.opencontainers.image.version=stage5-v2 -t stockflow-backend:stage5-v2 backend
kind load docker-image --name stockflow-lab stockflow-backend:stage5-v2
helm history stockflow --kube-context kind-stockflow-lab -n stockflow
helm upgrade stockflow ./helm/stockflow --kube-context kind-stockflow-lab -n stockflow \
  -f ./helm/stockflow/values-local.yaml --set backend.image.tag=stage5-v2 --wait --timeout 6m
kubectl --context kind-stockflow-lab -n stockflow rollout status deployment/backend --timeout=240s
# Reemplazar REVISION_ANTERIOR por la revisión registrada antes del upgrade.
helm rollback stockflow REVISION_ANTERIOR --kube-context kind-stockflow-lab -n stockflow --wait --timeout 6m
kubectl --context kind-stockflow-lab -n stockflow rollout status deployment/backend --timeout=240s
helm history stockflow --kube-context kind-stockflow-lab -n stockflow
```

Cada upgrade muestra explícitamente su archivo values; el override 3 no queda accidentalmente activo al volver a dos. Helm rollback restaura la configuración de la release completa y crea una nueva revisión. `kubectl rollout undo` sólo restaura el PodTemplate del Deployment, no ConfigMaps, Services, PDB ni values; evitar mezclarlo con Helm. Ninguno revierte migraciones Flyway, escrituras SQL ni Secrets externos. Con tres réplicas y surge=1 puede haber cuatro pools Hikari de diez conexiones; PostgreSQL conserva max_connections=50. Escalar más requiere revisar ese presupuesto y los recursos del nodo. PDB minAvailable=1 protege disrupciones voluntarias con dos réplicas; con una puede bloquear eviction. No protege fallos de nodo ni regula el rollout.

## Uninstall y persistencia

```bash
helm uninstall stockflow --kube-context kind-stockflow-lab -n stockflow --wait --timeout 4m
kubectl --context kind-stockflow-lab -n stockflow get pvc,secrets
```

Se eliminan workloads, Services, ConfigMaps, ServiceAccount, Role, RoleBinding y PDB. Los Secrets externos y Namespace quedan fuera de la release. PostgreSQL mantiene `volumeClaimTemplates` y política explícita `whenDeleted: Retain / whenScaled: Retain`; su PVC `data-database-0` lo crea el StatefulSet, no Helm, y se reutiliza al recrearlo con el mismo nombre.

Los tres PVCs explícitos de observabilidad usan `helm.sh/resource-policy: keep` para evitar pérdida accidental de TSDB, usuarios y silencios. [Helm documenta](https://helm.sh/docs/v3/howto/charts_tips_and_tricks/) que quedan huérfanos al retirarse la release: no es backup ni protección frente a kubectl delete pvc, eliminación del namespace/cluster o reclaim del almacenamiento. No gestionar esos PVCs simultáneamente desde otra release. Al reiniciar Prometheus conservando TSDB, muestras de pods anteriores pueden seguir visibles hasta vencer el lookback de cinco minutos si el proceso salió antes de emitir marcadores de staleness. El harness espera esa convergencia; no borra historia ni cambia las consultas para ocultarla. Para reinstalar reutilizarlos de forma explícita:

```bash
helm upgrade --install stockflow ./helm/stockflow --kube-context kind-stockflow-lab -n stockflow \
  -f ./helm/stockflow/values-local.yaml \
  --set prometheus.storage.existingClaim=prometheus-data \
  --set grafana.storage.existingClaim=grafana-data \
  --set alertmanager.storage.existingClaim=alertmanager-data --wait --timeout 6m
```

Con existingClaim el Chart monta pero no crea ni modifica el PVC; conservar estos overrides en los siguientes upgrades. No reducir tamaños existentes ni cambiar volumeClaimTemplates esperando una migración: StatefulSet restringe esos cambios y la expansión depende del StorageClass. Respaldar y planificar cambios de almacenamiento por separado. `scripts/k8s-down.sh --delete-lab-data` continúa siendo la operación destructiva explícita del laboratorio original; uninstall no llama ese script.

## Validación reproducible

```bash
helm lint ./helm/stockflow
helm template stockflow ./helm/stockflow -n stockflow -f ./helm/stockflow/values-local.yaml > /tmp/stockflow-rendered.yaml
python3 scripts/helm-check.py /tmp/stockflow-rendered.yaml
kubectl --context kind-stockflow-lab apply --dry-run=server -f /tmp/stockflow-rendered.yaml
python3 -m unittest discover -s scripts/tests -v
```

El chequeo semántico usa Python/PyYAML disponible en el laboratorio, sin añadir herramientas al toolchain. Valida spec de cada manifiesto base, RBAC, labels, namespace, ausencia de Secrets/Namespace/ClusterRoles y contenido exacto de ConfigMaps compartidos. Las pruebas del script usan Helm real y sustituyen kubectl, kind y Docker: verifican referencias externas alternativas y detención ante un Secret faltante, sin contactar clusters. Requieren Helm en PATH. El único aviso lint es icon recomendado: metadato cosmético, sin efecto funcional. Al repetir kubectl apply --dry-run=server sobre recursos gestionados por Helm, kubectl puede avisar que falta last-applied-configuration: esa annotation pertenece al flujo apply clásico y la modificación anunciada es sólo simulada. No añadirla a recursos reales ni usar apply sin dry-run para gestionar esta release. Server-side apply con otro field manager puede además informar conflicto en volumeClaimTemplates; es ownership, no un error de esquema, y no se fuerza ni se adopta la infraestructura. No versionar renders ni evidencias.

Para reglas, usar promtool del contenedor existente montando `helm/stockflow/files/monitoring/prometheus/rules` en `/etc/prometheus/rules` y `monitoring/prometheus/tests` en `/etc/prometheus/tests`; `promtool check rules` y `promtool test rules` conservan los 15 escenarios. Alertmanager: `amtool check-config` sobre su fuente empaquetada. También pueden ejecutarse ambas herramientas dentro de sus pods.

El harness `scripts/helm-experiments.py` está restringido al contexto `kind-stockflow-helm-lab`, con instalación nueva en namespace `stockflow-helm` y kubeconfig dedicado. Requiere las imágenes v1/v2 importadas previamente. Ejecutar sobre base vacía porque el E2E comercial asume ese estado:

```bash
export KUBECONFIG=/tmp/stockflow-helm-tools/kubeconfig
python3 scripts/helm-experiments.py --output /tmp/stockflow-helm-tools/evidence --uninstall
```

Verifica E2E comercial real, 2→3→2 Ready/targets, 63 consultas Grafana y gauges por instancia con dos/tres réplicas, upgrade de tag con tráfico HTTP continuo, history/rollback Helm, 56 reglas health=ok, uninstall, conservación de los cuatro UID de PVCs y Secrets, reinstall y comparación SQL del estado comercial/migraciones. `--uninstall` acepta explícitamente retirar sólo esa release y restaurarla con PVCs conservados. Las evidencias quedan fuera del repo. Si el E2E ya pasó en ese directorio, `--skip-e2e` permite continuar sin volver a crear sus fixtures; exige evidencia previa de éxito. La espera de gauges considera el muestreo readiness de 15s además de discovery/scrape. `--resume-reinstall-check` revalida sólo datos, targets y dashboards desde el checkpoint de uninstall/reinstall, sin repetir operaciones ya verificadas. Resultados realmente ejecutados en [STATUS](STATUS.md).
