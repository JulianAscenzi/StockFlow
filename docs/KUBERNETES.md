# Kubernetes local — Etapa 5

Referencia educativa de Etapa 5 (`raw manifests/reference`). El flujo recomendado de Etapa 6 está en [HELM](HELM.md); `scripts/k8s-up.sh` ahora usa Helm y `scripts/k8s-up-raw.sh` conserva el flujo base. No aplicar ambos a un mismo namespace.

Laboratorio dedicado `stockflow-lab`, namespace `stockflow`. Esta referencia no usa cloud, Ingress, operadores, autoscaling ni Terraform. Los manifiestos son recursos Kubernetes base. Compose y la demo Vercel/Render mantienen sus configuraciones.

## Arquitectura

Navegador → port-forward frontend:5173 → Vite proxy `/api` → Service backend:8080 → dos pods Spring Boot → Service database:5432 → StatefulSet PostgreSQL 17 con PVC.

Prometheus descubre directamente los pods backend y consulta management:9091, incluso cuando no están Ready. Grafana usa Prometheus interno y Alertmanager recibe alertas por su Service. Ningún Service publica puertos host; todas las UIs usan port-forward en loopback. El Service backend sólo contiene 8080, nunca management. Namespace no es una barrera de red: kindnet no aplica NetworkPolicy; otros pods del laboratorio pueden conectar a management. RBAC de descubrimiento permite a Prometheus sólo get/list/watch de pods en stockflow. Las demás cargas no montan tokens del API server.

## Herramientas y cluster

Se eligió kind porque Docker ya está disponible y evita otra VM. Validación: kind 0.31.0, kubectl 1.35.0, Kubernetes 1.35.0, Docker 29.8.2. Binarios usados en `/tmp/stockflow-k8s-tools`, sin instalación global ni cambios al kubeconfig personal. Versiones fijadas para reproducir este laboratorio, no una política de actualizaciones.

Descargar los binarios correspondientes a la arquitectura del host desde los sitios oficiales; verificar sus checksums publicados antes de instalarlos. Para linux/amd64:

```bash
mkdir -p /tmp/stockflow-k8s-tools
curl -fL https://kind.sigs.k8s.io/dl/v0.31.0/kind-linux-amd64 -o /tmp/stockflow-k8s-tools/kind
curl -fL https://dl.k8s.io/release/v1.35.0/bin/linux/amd64/kubectl -o /tmp/stockflow-k8s-tools/kubectl
chmod +x /tmp/stockflow-k8s-tools/{kind,kubectl}
export PATH=/tmp/stockflow-k8s-tools:$PATH
export KUBECONFIG=/tmp/stockflow-k8s-tools/kubeconfig
kind create cluster --name stockflow-lab --image kindest/node:v1.35.0 --config k8s/kind.yaml
kubectl --context kind-stockflow-lab version
kubectl --context kind-stockflow-lab apply -f k8s/namespace.yaml
```

El script de despliegue siempre usa `kind-stockflow-lab`; los comandos manuales deben usar ese contexto. Mantener el KUBECONFIG anterior exportado durante toda la sesión. El cluster tiene un nodo: demuestra coordinación de pods, no tolerancia al fallo del nodo físico.

## Secrets

Nunca guardar credenciales en manifiestos ni en `.env` del repositorio. Crear archivos temporales con permisos 600 y valores aleatorios; no imprimirlos en logs ni pasar los valores mediante argumentos CLI:

```bash
umask 077
python3 - <<'PY'
from pathlib import Path
from secrets import token_hex
Path('/tmp/stockflow-k8s-tools/secrets.env').write_text(
    'POSTGRES_PASSWORD=' + token_hex(24) + '\n'
    'APP_ADMIN_PASSWORD=' + token_hex(18) + '\n'
    'APP_JWT_SECRET=' + token_hex(32) + '\n')
Path('/tmp/stockflow-k8s-tools/grafana.env').write_text('password=' + token_hex(18) + '\n')
PY
kubectl --context kind-stockflow-lab -n stockflow create secret generic stockflow-secrets \
  --from-env-file=/tmp/stockflow-k8s-tools/secrets.env
kubectl --context kind-stockflow-lab -n stockflow create secret generic grafana-secret \
  --from-env-file=/tmp/stockflow-k8s-tools/grafana.env
```

El email inicial es `admin@stockflow.local`; consultar el archivo temporal privado para ingresar. Grafana usa usuario `admin` y su archivo privado. Kubernetes Secret codifica, no cifra automáticamente; quien administra el cluster puede leerlo. No es una solución de gestión de secretos de producción. No regenerar contraseñas sobre PVCs existentes: bootstrap no cambia cuentas y POSTGRES_PASSWORD no rota usuarios inicializados. Conservar los archivos mientras se necesite el laboratorio y borrarlos al destruirlo.

## Despliegue y acceso

```bash
./scripts/k8s-up-raw.sh
kubectl --context kind-stockflow-lab -n stockflow port-forward --address 127.0.0.1 service/frontend 5173:5173
# En terminales adicionales, sólo cuando se necesiten las UIs:
kubectl --context kind-stockflow-lab -n stockflow port-forward --address 127.0.0.1 service/grafana 3000:3000
kubectl --context kind-stockflow-lab -n stockflow port-forward --address 127.0.0.1 service/prometheus 9090:9090
kubectl --context kind-stockflow-lab -n stockflow port-forward --address 127.0.0.1 service/alertmanager 9093:9093
```

Abrir http://127.0.0.1:5173. No publicar DB ni management. Port-forward se conecta a un pod, no demuestra balanceo del Service por sí mismo; el proxy Vite sí consulta el ClusterIP backend. Si se reemplaza el pod destino del port-forward, reiniciar ese comando.

`k8s-up-raw.sh` construye imágenes `stage5-v1`, carga imágenes y configura los ConfigMaps de observabilidad desde los archivos canónicos de `helm/stockflow/files/monitoring/`. Valida cada manifiesto con dry-run del servidor y espera rollouts. La importación usa la plataforma del host porque Docker con índices multi-arquitectura incompletos puede hacer fallar `kind load --all-platforms`. No hay registry externo para imágenes StockFlow. No mutar un tag ya desplegado: usar un tag nuevo y actualizar el Deployment.

## Probes y arranque

| Backend | Endpoint (9091) | Período | Timeout | Fallos |
| --- | --- | --- | --- | --- |
| Startup | `/actuator/health/liveness` | 5s | 2s | 36 |
| Liveness | `/actuator/health/liveness` | 10s | 2s | 3 |
| Readiness | `/actuator/health/readiness` | 5s | 4s | 2 |

Startup concede aproximadamente 180s para Spring/Flyway/JPA bajo CPU limitada; durante ese intervalo no corre liveness. No espera DB como condición permanente de vida; si DB impide arrancar desde cero, Boot puede terminar y Kubernetes reintentar. Después del arranque, DB DOWN sólo retira readiness. Timeout readiness de 4s supera adquisición Hikari de 3s, con margen HTTP; dos fallos evitan retirar por un único chequeo lento. Liveness permite unos 30s de fallo sostenido para evitar reinicios por pausas breves. Umbrales son presupuestos de laboratorio, requieren recalibración con carga real.

PostgreSQL usa startup y readiness `pg_isready`, no liveness que reinicie una base ocupada. Frontend comprueba HTTP `/`; observabilidad comprueba sus endpoints nativos de readiness.

Flyway permanece activo en ambas réplicas. PostgreSQL coordina migraciones mediante advisory lock transaccional de Flyway; no se modifican V1–V5. Bootstrap usa su advisory lock propio antes de count/insert. Verificar las dos réplicas sobre PVC nuevo y contar filas de `flyway_schema_history`/`application_users`; no borrar una base existente para repetirlo. [Configuración oficial del lock Flyway](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-postgresql-namespace/flyway-postgresql-transactional-lock-setting).

## Recursos y JVM

| Carga | CPU request / limit | Memoria request / limit |
| --- | --- | --- |
| Backend, cada réplica | 250m / 1 CPU | 512Mi / 768Mi |
| Frontend Vite | 100m / 500m | 128Mi / 384Mi |
| PostgreSQL | 100m / 500m | 128Mi / 384Mi |
| Prometheus | 50m / 500m | 128Mi / 384Mi |
| Grafana | 50m / 500m | 256Mi / 768Mi |
| Alertmanager | 50m / 500m | 32Mi / 96Mi |

Son mínimos reservados para laboratorio pequeño, no capacidad comercial. Backend limita heap al 50% de 768Mi (~384Mi), dejando el resto para metaspace, stacks, buffers y JVM nativa. InitialRAMPercentage=20 evita reservar todo al inicio. ActiveProcessorCount=2 acota hilos de JVM y permite paralelismo moderado con una cuota de 1 CPU; no aumenta cuota. Verificar cgroup y JVM con `java -XshowSettings:system -XshowSettings:vm -version` dentro del pod. `/tmp` writable de 64Mi con raíz read-only; UID/GID 999 coincide con imagen backend. Vite requiere escritura en su caché de módulos, por eso su raíz sigue writable; usuario node 1000. Todos los contenedores rechazan privilege escalation, usan seccomp RuntimeDefault y descartan capabilities.

PostgreSQL usa shared_buffers=64MB y max_connections=50. Hikari conserva 10 por pod; dos réplicas y surge usan hasta 30, dejando margen para administración y probes. Grafana inicialmente se limitó a 384Mi: al renderizar ambos dashboards se observó memory.current≈384Mi, miles de eventos memory.max y fallos de readiness, sin OOMKill. Se amplió a 768Mi con request 256Mi; readiness de observabilidad admite 3s frente a throttling puntual. No se ocultó el problema aumentando sólo el timeout. Los requests/limits de observabilidad no garantizan 32d a carga alta; retención conserva el menor de 32d/2GB y la memoria deberá medirse con series reales.

## Persistencia

StatefulSet proporciona identidad `database-0` y PVC `data-database-0` de 2Gi. UID 70 y fsGroup 70 permiten usar la imagen Alpine no root; PGDATA usa un subdirectorio. Service headless proporciona DNS interno. No hay failover ni réplica DB. Prometheus (3Gi), Grafana (1Gi) y Alertmanager (1Gi) también usan PVCs; Deployments Recreate evitan escritores simultáneos.

Eliminar un pod conserva el PVC; eliminar el cluster destruye almacenamiento local. No es backup. **PostgreSQL dentro de Kubernetes se utiliza aquí para laboratorio; en producción/cloud evaluaremos una base administrada.** No se conectan ni borran volúmenes Compose.

## Self-healing, rollout y rollback

```bash
kubectl -n stockflow get pods -l app=backend
kubectl -n stockflow delete pod <pod-backend>
kubectl -n stockflow rollout status deployment/backend --timeout=240s
kubectl -n stockflow get endpointslices -l kubernetes.io/service-name=backend -o yaml
```

El controlador restaura Desired=2. El PDB `minAvailable: 1` protege evicciones voluntarias vía eviction API; no protege delete pod, fallos de nodo, liveness ni controla RollingUpdate. En un único nodo puede impedir drain completo mientras se conserva disponibilidad: no bajar esa protección sin aceptar el impacto.

Nueva imagen funcional con diferencia inocua en metadata:

```bash
docker build --label stockflow.lab.revision=stage5-v2 -t stockflow-backend:stage5-v2 backend
docker save stockflow-backend:stage5-v2 | docker exec -i stockflow-lab-control-plane \
  ctr --namespace=k8s.io images import --platform linux/amd64 --digests -
kubectl -n stockflow set image deployment/backend backend=stockflow-backend:stage5-v2
kubectl -n stockflow rollout status deployment/backend --timeout=240s
kubectl -n stockflow rollout history deployment/backend
kubectl -n stockflow rollout undo deployment/backend
kubectl -n stockflow rollout status deployment/backend --timeout=240s
```

`maxUnavailable=0`, `maxSurge=1`, `minReadySeconds=5`: el nuevo pod debe sostener readiness antes de retirar el anterior. Requiere memoria para tres JVM durante el rollout. Undo restaura el template anterior; no revierte datos ni migraciones. Probar tráfico continuo a través del frontend/Service durante ambas operaciones.

## Experimentos seguros

Usar únicamente este cluster y datos ficticios. DB DOWN: `kubectl -n stockflow scale statefulset/database --replicas=0`, inspeccionar probes y EndpointSlices; restaurar SIEMPRE `--replicas=1`, esperar readiness sin reiniciar backend. Self-healing: borrar una réplica. Persistencia: consultar datos antes/después de borrar database-0.

Graceful: mantener una entrada de stock esperando un lock de fila adquirido con psql; verificar `pg_stat_activity.wait_event_type='Lock'`, borrar ese pod, observar endpoint terminating/ready=false y liberar el lock dentro de 30s. La respuesta debe finalizar y los logs mostrar cierre HTTP/JPA/Hikari. Kubernetes concede 45s, superior a la fase Boot de 30s, sin preStop ni sleeps productivos. La propagación de EndpointSlices y el cierre son concurrentes: no se promete cero pérdida para toda conexión nueva durante ese intervalo.

Liveness: detener sólo la JVM de una réplica con SIGSTOP desde el namespace padre del nodo kind (PID 1 ignora esa señal sin handler desde su propio namespace); observar fallos de probe y evento Killing. Reanudar con SIGCONT para que la señal de terminación pendiente cierre normalmente y verificar incremento de restartCount. Restaurar SIGCONT en finally si el experimento se interrumpe. No es una prueba de cada posible deadlock; demuestra que kubelet toma acción ante un proceso que no responde. No usar sobre la base ni ambas réplicas a la vez.

## Observabilidad y múltiples réplicas

Descubrimiento `role: pod` con Role namespaced, filtro app=backend y puerto management. Se conserva instance=IP:9091 y labels pod/namespace; no se filtra readiness porque perderíamos evidencia de DB DOWN. No hay IDs comerciales en labels. Pod/instance cambian en rollout: cardinalidad de infraestructura acotada al número de pods y churn del laboratorio.

Las 50 reglas agregan rate/increase entre instancias; histogram_quantile usa `sum by(le)` antes de calcular p50/p95/p99. Los SLOs son de servicio. Los tres counters de negocio usan sum(rate(...)), sum by(reason) y sum by(type), respectivamente. Un reinicio lleva counters a cero: rate/increase detectan reset por serie antes de sumar; el bruto sum(counter) no es contabilidad ni persistencia. Eventos entre scrapes pueden perderse.

BackendNotScrapeable ahora se activa cuando sum(up)=0 o no existen targets, conserva for=1m. Una réplica caída con otra UP no dispara esa alerta. Readiness y saturación conservan diagnóstico por instancia; burn y latencia permanecen agregados. Reglas, dashboards y Alertmanager se reutilizan desde helm/stockflow/files/monitoring, sin perder Compose. No se agrega Operator/ServiceMonitor.

```bash
kubectl -n stockflow exec deployment/prometheus -- promtool check config /etc/prometheus/prometheus.yml
kubectl -n stockflow exec deployment/prometheus -- promtool check rules /etc/prometheus/rules/stockflow-recording.yml /etc/prometheus/rules/stockflow-alerts.yml
docker run --rm --entrypoint promtool -v "$PWD/monitoring/prometheus:/etc/prometheus:ro" -v "$PWD/helm/stockflow/files/monitoring/prometheus/rules:/etc/prometheus/rules:ro" \
  prom/prometheus:v3.14.0 test rules /etc/prometheus/tests/stockflow-rules.test.yml
kubectl -n stockflow exec deployment/alertmanager -- amtool check-config /etc/alertmanager/alertmanager.yml
```

## Diagnóstico

```bash
kubectl -n stockflow get all
kubectl -n stockflow get pods
kubectl -n stockflow get deployments
kubectl -n stockflow get services
kubectl -n stockflow get pvc
kubectl -n stockflow get endpointslices
kubectl -n stockflow describe pod <pod>
kubectl -n stockflow logs <pod> --timestamps
kubectl -n stockflow logs -f <pod>
kubectl -n stockflow logs <pod> --previous
kubectl -n stockflow get events --sort-by=.lastTimestamp
kubectl -n stockflow top pods
```

`top` requiere metrics-server: no se instala en esta etapa; si no existe, usar cgroups, Micrometer y Prometheus. ImagePullBackOff de StockFlow: verificar tags y carga de imágenes en el nodo correcto. Pending: revisar PVC/eventos y memoria reservada. CrashLoop: revisar logs --previous, credenciales, conexión DB y OOMKilled. Probes 503 sin reinicios: readiness se comporta como diseñado; revisar DB. No repetir create secret con una contraseña nueva sobre datos ya inicializados.

## Retirar laboratorio

Parar port-forwards con Ctrl-C. Para conservar datos, mantener el cluster. El script de eliminación requiere aceptación explícita del borrado:

```bash
./scripts/k8s-down.sh --delete-lab-data
```

Borra sólo el cluster `stockflow-lab` y sus PVCs. No toca Compose. Retirar después los archivos temporales de secretos y kubeconfig.

## Evidencia y límites

Los resultados medidos se registran en STATUS y en la sección de validación que se completa después de ejecutar los experimentos. YAML válido no implica validación de orquestación. No hay alta disponibilidad DB/nodo, TLS, backups, cluster multi-nodo, probe externa, deadlines de queries/red silenciosa ni sizing comercial. PDB, counters, port-forward y SLOs tienen los límites explicados arriba. No avanzar a Helm, Terraform ni cloud en esta etapa.

### Harness de experimentos

Con frontend port-forward activo, PATH/KUBECONFIG anteriores y Python 3:

```bash
python3 scripts/k8s-experiments.py self-healing --output /tmp/stockflow-k8s-tools/evidence
python3 scripts/k8s-experiments.py db-down --output /tmp/stockflow-k8s-tools/evidence
python3 scripts/k8s-experiments.py graceful --output /tmp/stockflow-k8s-tools/evidence
python3 scripts/k8s-experiments.py liveness --output /tmp/stockflow-k8s-tools/evidence
# Después de construir/importar v2:
python3 scripts/k8s-experiments.py rollout --output /tmp/stockflow-k8s-tools/evidence
python3 scripts/k8s-experiments.py persistence --output /tmp/stockflow-k8s-tools/evidence
```

El harness fija contexto/namespace, obtiene credenciales desde Secret sin imprimirlas y escribe evidencia fuera del repo. Requiere al menos un producto para graceful y una venta para persistencia; ejecutar primero el E2E comercial sobre base nueva. `liveness` usa Docker/crictl del nodo kind para señalar la JVM desde su namespace padre; no cambia securityContext del pod. No ejecutar experimentos simultáneos entre sí. Los fallos de assertions no autorizan declarar la etapa validada; revisar la evidencia y restaurar cargas antes de repetir. Rollout requiere imagen v2 ya cargada.

Para el E2E Kubernetes, definir E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD mediante un entorno privado, E2E_K8S_URL=http://127.0.0.1:58173 y ejecutar desde frontend:

```bash
npx playwright test --config playwright.k8s.config.ts
```

Reutiliza exactamente el flujo comercial existente, sin levantar otro Vite ni backend. Requiere una base nueva de laboratorio (asserts de stock/dashboard inicial). Las 28 pruebas de regresión completas conservan su runner aislado `npm run test:e2e`: dependen de credenciales/fixtures comunes y orden/estado inicial; no se migra toda esa arquitectura para esta etapa.

### Resultados medidos (2026-10-03)

Arranque simultáneo: dos réplicas Ready, V1–V5 aplicadas exactamente una vez, una sola cuenta inicial. Self-healing: 26,59s, 121 requests 200. DB DOWN: liveness UP/readiness DOWN, endpoints no Ready y restartCounts 0/0; recuperación automática. Balanceo: 55/45 de 100 lecturas por Service. Graceful: request bloqueada finalizó 200 al liberar lock, endpoint terminating/no Ready, cierre HTTP/JPA/Hikari y exit 143 capturado; 122 lecturas paralelas 200. Liveness: SIGSTOP/SIGCONT disparó reinicio kubelet, count 0→1, recuperación 49,33s.

Rolling v1→v2 y rollback v2→v1: 232 y 237 lecturas respectivamente, todas 200; total 103,08s. Persistencia: categoría/producto/venta/stock/usuario/migraciones = 1/1/1/5/1/5 antes y después de borrar DB, recuperación 16,92s. Todas las réplicas retiradas: alerta de servicio recibida por Alertmanager en 96,32s y resuelta tras restauración. Los tiempos son muestras, no SLOs prometidos.

Validación final: 385 backend, 28 Playwright aislados y un E2E comercial Kubernetes; 50 recording rules, seis alertas y 15 escenarios promtool; 63 consultas de dashboards a través de Grafana y ambos renderizados sin errores. Siete pods Ready, cuatro PVCs Bound, dos targets UP, reglas health=ok, cero alertas activas y counts finales 0/0. Evidencia detallada en [STATUS](STATUS.md); capturas/logs privados del laboratorio en /tmp/stockflow-k8s-tools. La revisión final pasó git diff --check y no hizo staging/commit/push.

### Inventario de archivos

Nuevos: `k8s/namespace.yaml`, `k8s/kind.yaml`, `k8s/config.yaml`, `k8s/backend/backend.yaml`, `k8s/frontend/frontend.yaml`, `k8s/database/database.yaml`, `k8s/observability/rbac.yaml`, `k8s/observability/prometheus.yaml`, `k8s/observability/prometheus.yml`, `k8s/observability/grafana.yaml`, `k8s/observability/alertmanager.yaml`, `scripts/k8s-up.sh`, `scripts/k8s-down.sh`, `scripts/k8s-experiments.py`, `frontend/playwright.k8s.config.ts` y esta guía.

Modificados: README, ARCHITECTURE, OPERATIONS, OBSERVABILITY, SRE, ROADMAP, STATUS; `frontend/e2e/commerce.spec.ts`; reglas/tests Prometheus, configuración Alertmanager y ambos dashboards en monitoring. No cambia código de negocio, esquema, configuración Compose ni despliegue Vercel/Render.

La revisión visual final encontró que lastNotNull sobre gauges por instancia podía conservar pods eliminados en tarjetas UP/uptime/readiness/edad. Esas tarjetas consultan ahora instant=true/range=false, mostrando sólo instancias actuales; los gráficos históricos conservan sus series y los SLOs agregados no cambian.
