# Objetivos de confiabilidad de StockFlow

Un SLI es una medida de lo que recibe el usuario; un SLO fija una meta para ese SLI durante una ventana. El error budget es el margen permitido de eventos malos. Estos objetivos iniciales son hipótesis operativas para un laboratorio local, no compromisos comerciales ni una historia de producción certificada.

## Métricas de negocio elegidas

Se evaluaron cuatro candidatas y se implementan sólo tres. Catálogo/categorías permiten preparar el comercio; autenticación protege acceso y dashboard consulta el estado existente. Su creación o lectura no necesita otro counter de negocio: ya tienen HTTP y pruebas funcionales. El valor principal es vender sin perder historial/stock y registrar movimientos confirmados.

| Métrica Micrometer → Prometheus | Qué mide y decisión que habilita | Labels / series por instancia |
| --- | --- | --- |
| stockflow.sales.confirmed → stockflow_sales_confirmed_total | Nuevas ventas confirmadas: distinguir actividad comercial efectiva de requests/reintentos; investigar si cesa mientras hay rechazos/tráfico | Ninguno / 1 |
| stockflow.sales.rejected → stockflow_sales_rejected_total | Intentos rechazados por stock insuficiente o producto inactivo: revisar abastecimiento/catálogo sin confundirlo con caída técnica | reason=INSUFFICIENT_STOCK o INACTIVE_PRODUCT / 2 |
| stockflow.stock.movements → stockflow_stock_movements_total | Cantidad de movimientos persistidos, no unidades: distinguir entradas/salidas y comprobar que la actividad de inventario acompaña operaciones | type=IN o OUT / 2 |

Total esperado: cinco series de counters por instancia, precreadas en cero. Prometheus añade job/instance. Ninguna usa productId, categoryId, saleId, SKU, email, usuario, JWT, request ID, idempotency key, nombres libres, timestamps ni el motivo libre del movimiento. Los gauges técnicos readiness y checked timestamp tampoco llevan labels propios: el timestamp es valor, nunca etiqueta.

La cuarta candidata, cruces de stock mínimo, se pospone: una edición del mínimo o reactivación cambia su interpretación; el dashboard de dominio ya consulta bajo stock. Tampoco se agregan fallos de inventario duplicando 409 HTTP ni métricas monetarias/counters por cada entidad.

BusinessMetrics registra sincronizaciones de la transacción real. Ventas y movimientos se incrementan en afterCommit del límite exterior; un rollback después de una línea de venta o después de guardar una venta descarta todos esos éxitos. Los rechazos conocidos se incrementan en afterCompletion sólo si terminó en rollback: son **intentos rechazados**, no operaciones confirmadas. Repetir un intento rechazado puede sumar otro rechazo, y está documentado como tal. Errores técnicos/validación/404 no se convierten en un reason genérico.

IdempotentSaleService sólo llama a confirmar si no encuentra una confirmación previa. Un replay carga detalle, por lo que no programa éxito ni movimientos otra vez. Rollback también revierte sale_confirmations, permitiendo reintentar la misma clave y contar sólo el commit final. Requests sin clave no ofrecen deduplicación; una escritura nueva realmente confirmada es otra venta.

Son counters en memoria: se reinician con la JVM y Prometheus conserva su historia. Un crash entre commit y callback/scrape puede perder telemetría; no se garantiza entrega exactly-once ni se agrega outbox a esta etapa. El historial PostgreSQL sigue siendo la fuente de verdad contable. Las métricas no reconstruyen ventas anteriores al arranque. Ocho pruebas de counters verifican transacciones, idempotencia y labels con MeterRegistry/PostgreSQL; dos pruebas adicionales cubren readiness cacheada.

## Requests elegibles y disponibilidad

El SLI es `requests exitosas elegibles / requests elegibles`. Se utiliza una allowlist de las rutas MVC normalizadas actuales: dashboard, categorías y sus detalles, productos/detalles/lookup/search/active/category/activate/deactivate, historial y ajustes de stock, ventas y sus detalles. La expresión exacta está en `monitoring/prometheus/rules/stockflow-recording.yml`; no depende de URLs crudas ni IDs. Incluye todas las instancias del job stockflow.

- Éxitos: HTTP 2xx y 3xx en esas rutas.
- Fallos elegibles: todos los HTTP 5xx de esas rutas, incluido `503 DATABASE_UNAVAILABLE`.
- Excluidos del numerador y denominador: todos los 4xx. Esto incluye validación, autenticación, recursos inexistentes, stock insuficiente, producto inactivo e idempotencia en conflicto. No constituyen automáticamente indisponibilidad técnica.
- Excluidos: login/auth, health/liveness/readiness, scraping, recursos estáticos, rutas desconocidas y requests que nunca llegan al servidor.

Se trata de un SLI del backend que completa requests, no del recorrido completo navegador/red/proxy. Si la JVM está detenida, no genera nuevos HTTP counters: el SLI de requests no puede representar esas pérdidas. La alerta de scraping cubre ese hueco operativo; no se inventan requests fallidas para rellenarlo. Futuras regresiones que produzcan 4xx incorrectos tampoco serían detectadas por este SLI: requieren pruebas funcionales y eventualmente una sonda externa. Si se introduce rate limiting, decidir explícitamente si 429 representa cliente o capacidad antes de mantener esta exclusión.

## SLOs iniciales

| Nombre | SLI | Objetivo | Ventana |
| --- | --- | --- | --- |
| Backend availability | Éxitos elegibles / elegibles | 99,5% | Rolling 30d |
| Read latency | Lecturas exitosas en ≤500ms / lecturas exitosas | 95% | Rolling 30d |
| Sale confirmation latency | POST /api/sales exitosos en ≤1s / confirmaciones HTTP exitosas | 95% | Rolling 30d |

Lecturas son GET de la allowlist; ventas son POST /api/sales. Latencia se condiciona a 2xx/3xx; los errores ya afectan disponibilidad y no se presentan como requests rápidas exitosas. Una repetición idempotente sí cuenta como experiencia HTTP y puede ser rápida, aunque no sea otra venta de negocio. Son metas independientes: aprobar latencia no implica aprobar disponibilidad.

La Etapa 3 observó p95 de lecturas de pocos milisegundos y degradación a unos 3s sin DB. Eso da margen para 500ms de lecturas. El límite 1s de ventas sigue siendo provisional: las pruebas funcionales no equivalen a un estudio de carga o hardware comercial. No hay evidencia para 99,999%; 99,5% permite aprender operación sin prometer confiabilidad no demostrada. Los histogramas nativos continúan; se agregan sólo buckets exactos de 500ms y 1s para calcular fracciones bajo esos umbrales sin interpolación. p50/p95/p99 siguen siendo aproximaciones por histogram_quantile.

Prometheus conserva hasta 32d o 2GB, lo que ocurra primero. La ventana 30d consulta sólo datos realmente disponibles; no extrapola una certificación mensual. `stockflow:slo_history:coverage30d` informa la fracción de muestras esperadas de las reglas de 15s que se conserva. No significa que hubo tráfico durante todo ese tiempo. Una instalación reciente, apagados, límites de espacio o scrapes perdidos dan cobertura parcial. Los paneles lo indican. Si no hay requests elegibles, ratios y percentiles usan -1, presentado como «Sin tráfico / inicializando», nunca 100% inventado. Burn=0 sin tráfico significa gasto no observado, no servicio demostrado sano.

Los counters no necesariamente tienen una muestra cero antes de la primera request de una ruta: ese primer incremento puede quedar fuera de `increase`. Los counters de negocio sí se preregistran en cero. Reinicios se tratan mediante rate/increase antes de agregar instancias; faltantes de scraping y crashes siguen limitando la evidencia.

## Error budget: matemática

Para disponibilidad: `1 - 0,995 = 0,005`, es decir **0,5%** de las requests elegibles.

Sea N el incremento observado de requests elegibles de 30d y E el incremento de errores elegibles:

- Budget total B = `0,005 × N` requests fallidas permitidas.
- Errores consumidos = E.
- Fracción consumida = `E / B`; puede superar 1 (100%) si se agota.
- Fracción restante = `max(0, 1 - E/B)`.
- Availability = `1 - E/N` cuando N > 0.
- Burn rate de una ventana W = `(errores_W / elegibles_W) / 0,005`.

Ejemplo: 10.000 elegibles permiten 50 fallos. Si hubo 10, se consumió 20% y queda 80%. Con 1% de error sostenido, burn=2. Burn=1 gasta al ritmo permitido; burn>1 consume más rápido. Increase extrapola límites de muestreo, por lo que los conteos pueden ser fraccionarios: son estimaciones operativas, no registros contables ni ventas históricas.

## Reglas y consultas

Las reglas cortas evalúan cada 15s y las de 30d cada minuto. Todas están versionadas en `monitoring/prometheus/rules`. Las 50 recording rules agrupan requests/errores/rates, disponibilidad, budget, objetivos, fracción rápida y percentiles por operación.

```promql
stockflow:availability:ratio5m
stockflow:availability:ratio30d
stockflow:eligible_requests:increase30d
stockflow:eligible_errors:increase30d
stockflow:error_budget:total30d
stockflow:error_budget:consumed_ratio30d
stockflow:error_budget:remaining_ratio30d
stockflow:error_budget:burn_rate5m
stockflow:latency:ratio30d{operation="read"}
stockflow:latency:ratio30d{operation="sale"}
stockflow:latency:p95_seconds5m{operation="sale"}
```

Las consultas largas no se repiten en los paneles. El dashboard adicional StockFlow - SRE Overview conserva el Application Overview anterior, y añade disponibilidad, cobertura, budget, latencia separada, dependencia/readiness, alertas y tres paneles de actividad de negocio. No se crea un tercer dashboard para sólo tres gráficos.

## Alertas y burn rate

Se usan dos pares de ventanas: la larga detecta consumo acumulado y la corta confirma que continúa. No basta con availability mensual bajo el objetivo. Los umbrales se derivan para este SLO:

- Rápida: burn >12 en 1h y 5m implica error >6%. Si persiste a volumen uniforme, consume `12 × 1/720 = 1,67%` del budget mensual en una hora; agotaría un budget intacto en 60h. Severidad critical, for 2m, al menos 20 elegibles y 3 errores en 5m.
- Sostenida: burn >4 en 6h y 30m implica error >2%. Consume `4 × 6/720 = 3,33%` del budget mensual en seis horas; agotaría uno intacto en 180h. Warning, for 10m, al menos 50 elegibles y 5 errores en 30m, y 3 errores recientes en 5m. Este último requisito evita iniciar una advertencia tardía cuando la causa ya fue reparada; el budget histórico sigue visible.

Estas proyecciones suponen distribución uniforme de requests; la decisión real usa eventos, no minutos caídos. Los mínimos son protecciones iniciales para tráfico escaso: un fallo aislado no debe alertar. También reducen sensibilidad con muy poco tráfico; readiness/scraping cubren caídas importantes sin depender de volumen. Las ventanas largas recién iniciadas usan historia parcial: interpretar las alertas junto a cobertura.

| Alerta | Condición resumida | for | Severidad |
| --- | --- | --- | --- |
| StockFlowAvailabilityBudgetFastBurn | Burn 1h y 5m >12, mínimos de tráfico/error | 2m | critical |
| StockFlowAvailabilityBudgetSustainedBurn | Burn 6h y 30m >4, mínimos de tráfico/error y errores recientes | 10m | warning |
| StockFlowBackendNotScrapeable | ninguna réplica scrapeable o todos los targets ausentes | 1m | critical |
| StockFlowReadinessDown | Readiness=0 con scraper UP y chequeo reciente (<45s) | 2m | critical |
| StockFlowSaleLatencyDegraded | Menos de 95% exitosas ≤1s, ≥20 exitosas y ≥3 lentas en 5m | 5m | warning |
| StockFlowHikariSaturated | Pending>0 y uso del pool ≥90%, scraper UP | 2m | warning |

Las expresiones exactas, impacto, acción y runbook_url están en stockflow-alerts.yml. No se agrega una alerta DB_UNAVAILABLE separada: los 503 ya consumen budget, readiness detecta dependencia y duplicar avisos por la misma caída sería ruido. Tampoco CPU, memoria, 4xx aislados ni una única request lenta.

La política multi-window se inspira en [Google SRE](https://sre.google/workbook/alerting-on-slos/), con umbrales y mínimos calculados para StockFlow, no copiados sin contexto.

## Alertmanager y validación

Prometheus envía alertas a `alertmanager:9093`. Alertmanager 0.34.1 agrupa por servicio y nombre, espera 10s, actualiza grupos cada 30s y repetiría cada 4h. El receptor local-only no tiene integraciones: la UI en http://localhost:9093 muestra alertas sin enviar mensajes externos. Puerto loopback configurable con ALERTMANAGER_PORT. El volumen alertmanager_data conserva silencios/estado; no se versiona. El gossip de cluster se desactiva para esta instancia local.

Inhibe readiness/pool cuando ninguna instancia del servicio es scrapeable y el burn sostenido cuando está activo el rápido. No oculta la coexistencia de budget consumido y caída de DB, que responden preguntas diferentes. `runbook_url` utiliza rutas conceptuales `docs/RUNBOOK.md#...`; todavía no hay un sitio público sirviéndolas. Abrirlas desde el repositorio.

```bash
docker compose exec prometheus promtool check config /etc/prometheus/prometheus.yml
docker compose exec alertmanager amtool check-config /etc/alertmanager/alertmanager.yml
docker compose run --rm --no-deps --entrypoint promtool \
  -v "$PWD/monitoring/prometheus/tests:/etc/prometheus/tests:ro" \
  prometheus test rules /etc/prometheus/tests/stockflow-rules.test.yml
```

Los tests de reglas usan series sintéticas deterministas para matemática, exclusiones y transiciones; no reemplazan los experimentos reales. La tolerancia fuzzy_compare ignora sólo el último bit de mantisa. Prometheus muestra pending/firing en /alerts y reglas en /rules; Alertmanager muestra firing recibidas y después su retirada al resolver. La evidencia de laboratorio está en STATUS.

## Cambiar objetivos

Actualizar explícitamente targets, divisores de budget, umbrales derivados y ventanas en las reglas; agregar/modificar buckets exactos en application-observability.properties si cambian los límites de latencia. Actualizar títulos/documentación y tests promtool, revisar runbooks, rebuild de backend y recarga/reinicio Prometheus. No basta con editar el número del panel. No cambiar objetivos para ocultar un incidente. Antes de adoptar metas comerciales, medir carga representativa, disponibilidad del recorrido completo, volumen mínimo y retención real. No se agregan Kubernetes, cloud ni tracing en esta etapa.

## SLOs con múltiples réplicas

Las 50 recording rules ya calculan SLIs de servicio mediante sum(rate/increase) y sum by(le) para percentiles. Se mantienen sin cambios matemáticos. BackendNotScrapeable pasa a `sum(up{job="stockflow"}) == 0 or sum(absent(up{job="stockflow"}))`: una réplica UP evita declarar caída completa; ausencia de targets sí alerta. El resultado carece de labels de instancia y Alertmanager inhibe readiness/pool por service cuando está activa esa caída total. Readiness y Hikari siguen por instancia; el resto de las seis alertas conserva sus umbrales. Promtool incluye una réplica caída, todas caídas, ausencia total y reset de counters. Ver evidencia y límites de Kubernetes en [KUBERNETES](KUBERNETES.md). Counters brutos no son persistencia comercial y el SLI no observa tráfico que no llegó a la JVM.
