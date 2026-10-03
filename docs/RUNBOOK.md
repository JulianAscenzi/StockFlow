# Runbooks locales de StockFlow

Usar la configuración habitual de Compose; para un proyecto aislado agregar `--env-file` y `-p` a cada comando. Estos procedimientos empiezan por diagnóstico y no autorizan borrar volúmenes ni terminar transacciones de un comercio. La definición de SLI y límites está en [SRE](SRE.md).

## Disponibilidad degradada

1. **Síntoma:** FastBurn o SustainedBurn; errores elegibles y consumo del budget crecen.
2. **Impacto:** algunas operaciones del catálogo, inventario o ventas no completan; pueden requerir recuperación idempotente.
3. **Primero:** mirar coverage/traffic, status 5xx, rutas, readiness y cambios recientes. No interpretar 4xx como incidentes de disponibilidad.
4. **PromQL:** `stockflow:error_budget:burn_rate5m`, `stockflow:error_budget:burn_rate1h`, `stockflow:eligible_errors:rate5m`, `sum by(uri,status)(rate(http_server_requests_seconds_count{job="stockflow",status=~"5.."}[5m]))`.
5. **Logs:** backend por requestId, DATABASE_UNAVAILABLE o stack de error inesperado; contrastar database.
6. **Comandos:** `docker compose ps`, `docker compose logs --since 15m backend database`; consultar probes con `docker compose exec backend curl -i http://backend-management:9091/actuator/health/readiness`.
7. **Causas:** DB caída, errores de código, locks o saturación; un scrape perdido también puede limitar la evidencia.
8. **Recuperación:** reparar la dependencia o el cambio identificado; comprobar requests reales, readiness y rate. El budget histórico no se recupera instantáneamente: el burn baja al salir errores de la ventana corta.
9. **Escalar:** si no se identifica causa, hay integridad dudosa, reaparece tras reparar o continúa el burn; conservar request IDs y tiempos, sin credenciales.

## DB unavailable

1. **Síntoma:** ReadinessDown, liveness UP, scraper UP; API 503 DATABASE_UNAVAILABLE y timeouts Hikari.
2. **Impacto:** operaciones DB no funcionan; JVM viva no implica disponibilidad comercial.
3. **Primero:** estado PostgreSQL, pg_isready, errores de conexión y pool idle/pending. Chequear edad de stockflow_readiness_checked_timestamp_seconds; el gauge se refresca cada 15s fuera del scrape.
4. **PromQL:** `stockflow_readiness{job="stockflow"}`, `up{job="stockflow"}`, `sum(rate(http_server_requests_seconds_count{job="stockflow",status="503"}[5m]))`, `rate(hikaricp_connections_timeout_total{job="stockflow"}[5m])`.
5. **Logs:** `docker compose logs --since 15m database backend`, mensajes de conexión/SQLState y requestId de 503.
6. **Comandos:** `docker compose ps`; `docker compose exec database sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`; probe readiness como arriba.
7. **Causas:** contenedor detenido, reinicio DB, credenciales desalineadas, red o conexiones agotadas. Una variable de contraseña nueva no rota una DB existente.
8. **Recuperación:** si la DB del laboratorio fue detenida deliberadamente, `docker compose start database`; comprobar readiness, API y pool. No reiniciar automáticamente una JVM sana ni usar down -v. Antes de repetir ventas, usar la misma clave idempotente original cuando corresponda.
9. **Escalar:** problemas persistentes de conexión, almacenamiento, autenticación o evidencia de corrupción; el operador debe decidir cualquier acción destructiva.

## Alta latencia

1. **Síntoma:** SaleLatencyDegraded; fracción exitosa ≤1s baja y percentiles suben durante tráfico suficiente.
2. **Impacto:** confirmar ventas se demora aunque termine correctamente; no asumir que una request pendiente fue rechazada.
3. **Primero:** diferenciar lecturas/ventas, éxitos/5xx, observar pool pending, locks DB y CPU como contexto.
4. **PromQL:** `stockflow:latency:ratio5m{operation="sale"}`, `stockflow:latency:p95_seconds5m{operation="sale"}`, `stockflow:slow_sale_requests:increase5m`, `hikaricp_connections_pending{job="stockflow"}`.
5. **Logs:** requestId de requests problemáticas y advertencias Hikari; los logs actuales no registran cada request ni todas las consultas SQL.
6. **Comandos:** `docker compose logs --since 15m backend`; abrir psql con `docker compose exec database sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` y consultar la SQL de diagnóstico de abajo.
7. **Causas:** bloqueo pesimista esperado por transacciones largas, consultas lentas, capacidad de pool o recursos insuficientes.
8. **Recuperación:** finalizar correctamente el trabajo que sostiene un lock en el laboratorio, corregir query/transacción problemática y verificar latencia. No agregar sleeps ni ampliar timeouts para ocultar la causa.
9. **Escalar:** si los locks pertenecen a operaciones reales, hay ventas de resultado incierto o la degradación persiste; preservar claves de recuperación en el cliente sin copiarlas a labels.

```sql
SELECT pid, state, wait_event_type, wait_event, now() - xact_start AS transaction_age
FROM pg_stat_activity
WHERE datname = current_database() AND pid <> pg_backend_pid();
```

## Hikari saturado

1. **Síntoma:** HikariSaturated; pending>0 junto a active/max ≥90% por 2m.
2. **Impacto:** requests esperan conexión y pueden agotar el plazo de 3s.
3. **Primero:** comprobar DB y edad de transacciones, active/idle/pending/max y tiempo de adquisición. Pending con active=0 durante DB caída no es esta alerta: corresponde a readiness.
4. **PromQL:** `hikaricp_connections_active{job="stockflow"}/hikaricp_connections_max{job="stockflow"}`, `hikaricp_connections_pending{job="stockflow"}`, `rate(hikaricp_connections_acquire_seconds_sum{job="stockflow"}[5m])/rate(hikaricp_connections_acquire_seconds_count{job="stockflow"}[5m])`.
5. **Logs:** timeout/connection acquisition de Hikari y excepciones DB en backend.
6. **Comandos:** `docker compose logs --since 15m backend database`; consulta pg_stat_activity de la sección anterior.
7. **Causas:** transacciones largas, locks, query lenta, tráfico concurrente o capacidad DB insuficiente.
8. **Recuperación:** corregir la causa; no aumentar max indiscriminadamente: más conexiones pueden empeorar la DB. Validar desaparición de pending y recuperación de latencia.
9. **Escalar:** si no cae la espera o se propone dimensionar conexiones; requiere carga representativa y presupuesto DB por instancia.

## Backend no scrapeable

1. **Síntoma:** BackendNotScrapeable; up=0 o serie ausente por 1m.
2. **Impacto:** la JVM puede estar caída o haber perdido acceso a management; las métricas no pueden confirmar el servicio. Esto es distinto de PostgreSQL DOWN con up=1.
3. **Primero:** target /targets y lastError, proceso/contenedor, red metrics y listener 9091.
4. **PromQL:** `up{job="stockflow"}`, `absent(up{job="stockflow"})`; no interpretar un SLI HTTP quieto como disponibilidad normal.
5. **Logs:** backend por startup/error/shutdown, y Prometheus por fallos de scraping/configuración.
6. **Comandos:** `docker compose ps`, `docker compose logs --since 15m backend prometheus`; `docker compose exec prometheus wget -qO- http://backend-management:9091/actuator/prometheus`; verificar listener desde backend con curl si sigue vivo.
7. **Causas:** backend detenido, arranque fallido, listener ligado a otra interfaz, red rota o scrape timeout. Management no debe publicarse al host para solucionarlo.
8. **Recuperación:** para una parada deliberada de laboratorio, `docker compose up -d --wait`; corregir la causa en otros casos. Verificar up=1, probes y una request autenticada. Alertmanager debe retirar la alerta tras la resolución.
9. **Escalar:** arranque repetidamente fallido, error no identificado o imposibilidad de observar la JVM; conservar logs sin env/secretos.
