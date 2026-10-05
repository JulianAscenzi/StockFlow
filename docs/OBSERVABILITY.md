# Observabilidad local

Micrometer es la fachada de métricas que Actuator ya utiliza. El único módulo agregado es `micrometer-registry-prometheus`, con versión gestionada por Spring Boot 4.1.1. Boot instrumenta HTTP, JVM, proceso y Hikari; los counters de negocio de la Etapa 4 se describen en [SRE](SRE.md); no hay exporter PostgreSQL. Prometheus consulta periódicamente el backend y guarda series temporales; Grafana consulta Prometheus mediante PromQL y presenta los dashboards.

## Arranque y accesos

Completar `.env` según `.env.example`, incluyendo `GRAFANA_ADMIN_USER` y una contraseña propia en `GRAFANA_ADMIN_PASSWORD` (por ejemplo, generada con `openssl rand -hex 24`). No guardar `.env` en Git. Ejecutar `docker compose up --build`.

- Frontend: http://localhost:5173.
- Prometheus: http://localhost:9090; targets: http://localhost:9090/targets.
- Grafana: http://localhost:3000; ingresar con las credenciales de `.env` y abrir la carpeta StockFlow.

Los puertos se pueden cambiar con `FRONTEND_PORT`, `PROMETHEUS_PORT` y `GRAFANA_PORT`. Todos se publican en loopback. Grafana inicializa su administrador sólo al crear su base; cambiar la variable después no rota una cuenta persistida. Utilizar la administración de Grafana para rotarla.

## Flujo y límite de seguridad

```
Backend:9091 /actuator/prometheus → Prometheus:9090 → Grafana:3000
           scrape 15s / timeout 5s              PromQL
```

Se eligió un management port separado. En el puerto de aplicación, abrir anónimamente métricas dependería del proxy y de mantener siempre la API privada; además cambiaría esa política para la demo pública. El perfil `observability`, activado por Compose, expone únicamente `health,prometheus` en 9091. Fuera de ese perfil sigue expuesto solamente health en el puerto de aplicación, conservando el despliegue existente.

Compose vincula el listener de management a la dirección del alias `backend-management` en la red `metrics` interna. Sólo backend y Prometheus pertenecen a esa red. Frontend usa la red de aplicación; Grafana comparte con Prometheus la red `monitoring`. No se publica ningún puerto del backend. El puerto de aplicación sigue escuchando en las interfaces del backend; esta separación protege el listener de management, no constituye una sandbox para un Prometheus comprometido.

La cadena de seguridad de management permite GET anónimo únicamente a:

- `/actuator/prometheus`.
- `/actuator/health`.
- `/actuator/health/liveness`.
- `/actuator/health/readiness`.

Todo lo demás en management se deniega, incluso con autenticación del dominio desactivada. Env, configprops, heapdump y metrics no están expuestos; se deshabilita el índice de descubrimiento. Health nunca muestra detalles/componentes. No se permite `/actuator/**` globalmente. La decisión usa el puerto local real del servidor, nunca `X-Forwarded-Port`.

**En Compose las probes conservan sus rutas, pero pasan de 8080 a 9091**. Consultarlas desde backend usando `http://backend-management:9091/actuator/health/readiness` (análogamente para liveness y health). La configuración de Compose actualiza su healthcheck. El [laboratorio Kubernetes](KUBERNETES.md) ya conserva management sin publicación externa. NetworkPolicy sigue siendo opcional y no está aplicada: kindnet no la hace efectiva.

Prometheus usa `backend-management:9091`, nunca localhost; Grafana usa `http://prometheus:9090`. Scrape cada 15 segundos, timeout de 5 segundos: suficiente resolución local sin solicitudes excesivas. `up{job="stockflow"}` vale 1 si el último scrape fue exitoso; no representa readiness de la DB. Si el endpoint deja de responder, pasa a 0 después del siguiente scrape/timeout.

## Métricas y consultas

| Área | Nombres Prometheus |
| --- | --- |
| HTTP | `http_server_requests_seconds_count`, `_sum`, `_max`, `_bucket` |
| JVM | `jvm_memory_used_bytes`, `jvm_memory_max_bytes`, `jvm_memory_committed_bytes`, `jvm_threads_live_threads`, `jvm_threads_daemon_threads`, `jvm_gc_pause_seconds_count`, `_sum`, `_max` |
| Proceso | `process_cpu_usage`, `system_cpu_usage`, `process_uptime_seconds`, `process_start_time_seconds` |
| Pool | `hikaricp_connections_active`, `_idle`, `_pending`, `_max`, `_min`, `hikaricp_connections_timeout_total` |
| Tiempos pool | `hikaricp_connections_acquire_seconds`, `hikaricp_connections_usage_seconds`, `hikaricp_connections_creation_seconds`, con sufijos `_count`, `_sum`, `_max` |

Algunas métricas son dependientes del sistema, colector GC o eventos observados. Los gauges del pool muestran una muestra instantánea: `active=0` durante tráfico breve es normal; una operación puede terminar entre scrapes. `pending` mide solicitudes esperando conexión; `max` es la configuración de capacidad, no conexiones actualmente abiertas. No se observan locks, disco, replicación ni consultas internas PostgreSQL.

El dashboard filtra HTTP por `uri=~"/api/.*"` para excluir probes y scraping. Rate es requests por segundo, no el counter acumulado:

```promql
sum(rate(http_server_requests_seconds_count{job="stockflow",uri=~"/api/.*"}[5m]))
sum by (uri) (rate(http_server_requests_seconds_count{job="stockflow",uri=~"/api/.*"}[5m]))
sum by (status) (rate(http_server_requests_seconds_count{job="stockflow",uri=~"/api/.*"}[5m]))
```

Fracción de 5xx sobre todas las requests API:

```promql
(sum(rate(http_server_requests_seconds_count{job="stockflow",uri=~"/api/.*",status=~"5.."}[5m])) or vector(0))
/ sum(rate(http_server_requests_seconds_count{job="stockflow",uri=~"/api/.*"}[5m]))
```

Sin tráfico el cociente puede no tener datos. Los 4xx se muestran separados: autenticación, validaciones, recursos inexistentes y rechazos de negocio pueden ser esperables; no se consideran automáticamente fallos de infraestructura.

## Histogramas y percentiles

Se configura explícitamente `management.metrics.distribution.percentiles-histogram.http.server.requests=true`. No se publican percentiles calculados localmente. Se parte de los buckets predeterminados Boot/Micrometer: la Etapa 3 verificó 69 límites por combinación de etiquetas, desde 1ms hasta 30s más `+Inf`. La configuración actual añade los umbrales exactos de 500ms y 1s para los SLIs de latencia; aquel conteo es histórico, no el conteo actual. Los objetivos y su carácter provisional se explican en SRE. Los buckets permiten sumar observaciones entre réplicas antes de calcular percentiles. Las aproximaciones están condicionadas por su resolución; no son mediciones exactas de cada request.

El promedio es suma de duraciones / cantidad y puede ocultar una cola lenta. p50 es la mediana: aproximadamente 50% de requests tarda ese tiempo o menos. p95 cubre el 95%; p99 el 99%, útil para la cola lenta, pero inestable con pocas observaciones.

```promql
histogram_quantile(0.50, sum by (le) (rate(http_server_requests_seconds_bucket{job="stockflow",uri=~"/api/.*"}[5m])))
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="stockflow",uri=~"/api/.*"}[5m])))
histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{job="stockflow",uri=~"/api/.*"}[5m])))
```

Grafana sustituye `[5m]` por `[$__rate_interval]`, con intervalo del datasource de 15s. Se necesitan al menos dos muestras y observaciones en la ventana: un dashboard recién iniciado puede no mostrar percentiles. Para percentiles por endpoint, sumar `by (le, uri)`; para conservar separación por instancia, sumar `by (le, instance)`. No promediar percentiles entre réplicas.

## Cardinalidad

Se conserva la instrumentación nativa de Spring MVC: `uri` usa plantillas como `/api/products/{id}`, no URLs con IDs. Las etiquetas HTTP principales son `method`, `uri`, `status`, `outcome`, `exception` y, según la observación, `error`; Prometheus agrega `job` e `instance`. Las rutas no resueltas usan categorías acotadas de Boot. No se incorporan SKU, email, JWT, IDs del dominio, request ID, idempotency key ni parámetros de consulta. El request ID continúa sólo en logs/MDC.

Cada combinación de etiquetas y cada bucket produce una serie. Al agregar rutas o instrumentación futura, conservar categorías acotadas; nunca usar valores de usuarios como tags. Las pruebas y el tráfico de verificación comprueban IDs distintos sobre la misma plantilla.

## Provisioning y presentación

```text
monitoring/prometheus/prometheus.yml                 # Compose scraping
monitoring/prometheus/tests/stockflow-rules.test.yml # shared rule tests
helm/stockflow/files/monitoring/
├── prometheus/rules/                               # canonical rules
├── alertmanager/alertmanager.yml
└── grafana/
    ├── provisioning/datasources/prometheus.yml
    ├── provisioning/dashboards/stockflow.yml
    ├── dashboards/stockflow-overview.json
    └── dashboards/stockflow-sre.json
```

Datasource con UID estable `stockflow-prometheus`; dashboard `StockFlow - Application Overview`, UID `stockflow-overview`. Se carga al arrancar; no requiere clicks para reconstruirlo. Overview agrupa estado de scraping, requests/s, fracción 5xx, p50/p95/p99 y uptime. HTTP muestra rate por ruta/status, duración promedio/p95 y 4xx/5xx. JVM muestra heap usado/máximo/utilización, non-heap, threads y pausas GC. Proceso muestra CPU de JVM y sistema. Pool muestra active/idle/pending/max, utilización y tiempo medio de adquisición/uso. Unidades: segundos, bytes, requests/s y fracciones representadas como porcentaje.

Modificar el JSON versionado, no la copia runtime. El proveedor relee los archivos cada 10s y no permite editar la versión provisionada por UI. Para un diseño nuevo se puede trabajar en una copia en Grafana, exportar JSON y reemplazar el archivo; no incluir credenciales. El datasource es ineditable y se carga automáticamente.

## Persistencia y límites

Prometheus guarda su TSDB en `prometheus_data`, con retención de 32 días o 2 GB, lo que ocurra primero. Grafana guarda usuarios, preferencias y base interna en `grafana_data`. `docker compose down` conserva estos volúmenes; `down -v` elimina los datos. Cambiar el nombre de proyecto Compose utiliza otros volúmenes. Provisioning/configuración se monta read-only y se mantiene en Git; TSDB, bases internas, plugins runtime y secretos no se versionan.

Healthchecks utilizan wget incluido en las imágenes oficiales: Prometheus `/-/ready` y Grafana `/api/health`. No se instalan paquetes adicionales. La Etapa 4 agrega SLOs, métricas de negocio y alertas locales según [SRE](SRE.md). No hay tracing, logs centralizados ni exporter PostgreSQL. HTTP percentiles reflejan requests completadas; requests en curso no entran hasta finalizar. Readiness DOWN no detiene scraping ni transforma automáticamente `up` en 0.

Referencias: [integración nativa de Boot 4.1](https://docs.spring.io/spring-boot/4.1/reference/actuator/metrics.html), [histogramas Micrometer](https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html).

## Verificación manual segura

Comprobar el target en `/targets` y consultar almacenamiento, no sólo el texto del endpoint:

```bash
curl --get --silent http://localhost:9090/api/v1/query \
  --data-urlencode 'query=up{job="stockflow"}'
curl --get --silent http://localhost:9090/api/v1/query \
  --data-urlencode 'query=hikaricp_connections_max{job="stockflow"}'
```

Para tráfico moderado, obtener un bearer token mediante el login habitual y leerlo en una variable temporal (no guardarlo en Git):

```bash
read -r -s -p 'Bearer token: ' TOKEN
for i in $(seq 1 60); do
  curl --silent --output /dev/null --write-out '%{http_code}\n' \
    -H "Authorization: Bearer $TOKEN" http://localhost:5173/api/dashboard
  sleep 1
done
unset TOKEN
```

El script sólo lee; para una regresión de etiquetas usar también productos/categorías e IDs inexistentes. Cambiar puertos si se utiliza un proyecto aislado. Para repetir PostgreSQL DOWN, usar exclusivamente una base temporal: detener `database`, consultar las probes y API autenticada, observar `up`, HTTP/Hikari y después iniciar `database`. No ejecutar ese experimento sobre información comercial. La evidencia de la validación de esta etapa está en [STATUS](STATUS.md).

## Confiabilidad y actividad de negocio

Etapa 4: [SRE](SRE.md) centraliza SLIs, objetivos, budget, cardinalidad y reglas; [RUNBOOK](RUNBOOK.md) explica intervención. Alertmanager local-only está en http://localhost:9093 (loopback, ALERTMANAGER_PORT). El segundo dashboard StockFlow - SRE Overview se provisiona en la misma carpeta. Readiness se exporta mediante un gauge cacheado del health nativo, refrescado cada 15s, sin consultar DB en el scrape; la edad del chequeo permite reconocer datos viejos. Se añaden buckets exactos 500ms/1s a los histogramas HTTP existentes.

## Observabilidad de Kubernetes

La referencia raw de Kubernetes usa `k8s/observability/prometheus.yml`; el flujo Helm recomendado usa `helm/stockflow/files/prometheus-kubernetes.yml`, parametrizado por namespace/release. Ambos reutilizan rules, dashboards y Alertmanager canónicos mediante ConfigMaps. Descubrimiento de pods con RBAC local al namespace filtra backend/management y conserva targets Running aunque readiness esté DOWN. instance=IP:9091 y pod distinguen JVM; rate/increase se calculan antes de sumar, histogramas se agregan por le. Leyendas Hikari incluyen instance para no confundir pools con el mismo nombre. UI por port-forward en loopback; no se agrega Operator ni se altera scraping Compose.

La revisión visual final encontró que lastNotNull sobre gauges por instancia podía conservar pods eliminados en tarjetas UP/uptime/readiness/edad. Esas tarjetas consultan ahora instant=true/range=false, mostrando sólo instancias actuales; los gráficos históricos conservan sus series y los SLOs agregados no cambian.
