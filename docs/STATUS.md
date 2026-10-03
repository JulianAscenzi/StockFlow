# Status

## Completed modules

El backend MVP está cerrado: `category`, `product`, `inventory`, `common`, `sale` y `dashboard` están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

Etapa 4: métricas transaccionales, SLI/SLO provisionales, budget y alertas locales implementados y verificados el 2026-10-03. Guías: [SRE](SRE.md) y [RUNBOOK](RUNBOOK.md). 385 tests backend y28 escenarios Playwright aprobados; seis servicios Compose healthy. Sin staging/commit/push; se detiene al terminar esta etapa.

Etapa 3: observabilidad local implementada y verificada el 2026-10-03, sin staging, commit ni push. Guía central: [OBSERVABILITY](OBSERVABILITY.md). Backend 375 pruebas y navegador 28 escenarios aprobados; Compose con cinco servicios healthy.

Etapas 1 y 2 DevOps implementadas y verificadas el 2026-10-03, sin staging, commit ni push por instrucción del usuario. Compose inicia el entorno completo; el backend distingue probes, coordina bootstrap concurrente, correlaciona logs y conserva graceful shutdown. [OPERATIONS](OPERATIONS.md) documenta presupuestos y límites. Las notas de validación están al final. El alcance del MVP y la demo pública se conserva.

MVP de portfolio terminado. `auth` conserva un único administrador inicial configurable por entorno, contraseñas BCrypt, JWT HS256 de ocho horas y una pantalla de inicio de sesión para instalaciones privadas. La demo gratuita de portfolio permanece pública con autenticación desactivada intencionalmente y datos ficticios; en ese modo no se crean los componentes ni la ruta JWT y no se requiere `APP_JWT_SECRET`. El catálogo de productos se pagina en la interfaz y el selector de categorías carga todas sus páginas; ventas e inventario consultan una página por vez mediante búsqueda por nombre/SKU. La confirmación de venta bloquea sus controles mientras la solicitud está en curso.

## Deployment decision

El alcance aprobado es una demostración pública para CV: Vercel sirve la interfaz y Render Free la API con PostgreSQL. No se autorizan datos comerciales ni se contrata infraestructura de producción. La guía de despliegue explica la persistencia limitada, falta de backups y el smoke test de la demo.

## Last general test result

Etapa 4 — 2026-10-03: **385 tests backend y28 escenarios Playwright aprobados**, sin fallos/errores/omisiones; promtool/amtool, dashboards y experimentos pending/firing/resolved verificados.

Etapa 3 — 2026-10-03: **375 pruebas backend y 28 escenarios Playwright aprobados**, sin fallos, errores, omisiones ni reintentos. Compose con cinco servicios healthy; scraping, percentiles, PostgreSQL DOWN y persistencia verificados.

Verificación del 2026-10-03: **370 pruebas backend sin fallos, errores ni omisiones** y **28 escenarios de navegador aislado** aprobados. La compilación frontend fue verificada en la Etapa 1, sin cambios de fuentes frontend en la Etapa 2. Los checkpoints de auditoría se registran abajo.

## Cuaderno del proyecto

La carpeta `docs` puede abrirse como bóveda de Obsidian desde [Inicio](Inicio.md). Incluye una decisión sobre bloqueo de stock y un guion de demostración, con enlaces Markdown y configuración personal excluida de Git. El cambio documental se valida mediante revisión de enlaces locales y `git diff --check`; por autorización explícita del usuario no se ejecuta Maven para este cambio, dado que Docker no está disponible. El resultado general anterior corresponde a la última ejecución registrada, no a esta actualización documental.

## Pending decisions

Ninguna para el MVP de portfolio. Un uso comercial futuro requerirá decidir proveedor, backups, operación y secretos propios antes de cargar información real.

## Real blockers

Ninguno dentro del alcance de portfolio.

## Ampliaciones en curso

Docker es accesible con ejecución fuera del sandbox. La verificación inicial detectó una regresión previa: el advice convertía rutas inexistentes en 500; se corrige a 404 `RESOURCE_NOT_FOUND`, con regresión para login deshabilitado.

Verificación del 2026-10-01: regresión específica y suite backend completa aprobadas (315 pruebas).

Bloque 24 completado: Playwright aislado, cinco escenarios aprobados, compilación frontend aprobada y 315 pruebas backend aprobadas. El resumen vuelve a consultar al navegar y descarta respuestas obsoletas. Próximo bloque: CI.

Bloque 25: workflow CI con jobs backend, frontend y navegador dependiente de ambos. Ubuntu 24.04, Java 21, Node 24, Docker obligatorio, comprobación de pruebas ejecutadas sin omisiones e informes por siete días. La primera ejecución remota queda pendiente de que el usuario publique la rama; no se hizo push.

Bloque 26 completado: historial paginado y detalle histórico, sin alterar inventario. Pruebas específicas: 13; suite completa: 319; navegador: seis escenarios; compilación y whitespace aprobados. Próximo bloque: búsqueda paginada.

Bloque 27 completado: búsqueda paginada compartida, filtros PostgreSQL y selección conservada. Pruebas específicas: 66; suite completa: 322; navegador: ocho escenarios; compilación y whitespace aprobados. Próximo bloque: confirmación idempotente.

Bloque 28: confirmación idempotente implementada. La recuperación usa una clave UUID persistida en `sessionStorage`; las respuestas tardías sólo pueden limpiar la operación que las originó, evitando borrar una recuperación nueva tras navegar. La compilación frontend pasa. Las pruebas de integración con Docker/Testcontainers quedaron verificadas durante la auditoría del 2026-10-02.

## Auditoría general — 2026-10-02

Docker/PostgreSQL disponibles. La auditoría detectó una diferencia entre la fecha inicial de una venta (nanosegundos) y su recuperación idempotente (microsegundos de PostgreSQL). Se corrige la fecha de creación a precisión de microsegundos y se refuerza la regresión de igualdad de respuestas. La validación conjunta final pasó 54 pruebas específicas y 336 pruebas backend completas sin fallos, errores ni omisiones; la compilación frontend también pasó.

CORS ahora admite PATCH para activar/desactivar productos desde un origen permitido; se verifica el preflight de ambas rutas. Esta corrección está incluida en la validación conjunta de 54 pruebas específicas y 336 pruebas completas.

Se reprodujeron y corrigieron dos fallas de stock: editar/activar/desactivar sobrescribía stock concurrente; la confirmación utilizada por HTTP podía vender con stock obsoleto de la sesión JPA. El catálogo ahora bloquea antes de leer, y las ventas bloquean los productos en orden de ID antes de crear snapshots. Se agregan cuatro escenarios concurrentes deterministas y una prueba del orden de bloqueos. El [informe de auditoría](AUDIT.md) documenta cuatro bugs corregidos, siete hallazgos pendientes y sus prioridades. No se modificaron migraciones ni se hizo push.

## Productos inactivos y ventas — 2026-10-02

Primer hallazgo pendiente de la auditoría corregido: desactivar impide nuevas ventas. `SaleService` valida el estado actual bajo bloqueo pesimista en orden de ID para solicitudes HTTP y ventas preparadas, antes de descontar stock. El advice responde 409 `PRODUCT_INACTIVE`. La recuperación idempotente de una venta confirmada sigue devolviendo el historial; una clave rechazada puede reutilizarse tras reactivar. Los ajustes de inventario de inactivos permanecen permitidos. La interfaz muestra el mensaje de rechazo, conserva el carrito editable y limpia sólo la recuperación correspondiente.

Validación: 26 pruebas específicas; suite completa de 342 pruebas sin fallos, errores ni omisiones; compilación TypeScript/Vite; nueve escenarios de Chromium aislado aprobados. Las regresiones concurrentes observan la espera real de PostgreSQL y verifican ambos órdenes de venta/desactivación. Diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias; sin push.

## Revisión previa a publicación — 2026-10-02

La revisión de los cuatro fixes pendientes de publicación encontró una falla adicional: adquirir `PESSIMISTIC_WRITE` no refrescaba productos ya cargados en la sesión JPA. Dos regresiones PostgreSQL reprodujeron ventas aceptadas después de una desactivación concurrente confirmada, tanto por IDs como mediante agregados preparados. La lectura bloqueada se implementa en un fragmento del repositorio, compartido por ventas, inventario y catálogo: flush de cambios propios, bloqueo y refresh. Una tercera regresión verifica balances y estado al encadenar cambios de estado, inventario y venta dentro de una sola transacción. No cambia contratos HTTP, esquema ni dependencias.

Verificación final de la revisión: 45 pruebas específicas, 345 pruebas backend completas y nueve escenarios Chromium aislados aprobados, sin fallos ni omisiones. Compilación frontend y `git diff --check` aprobados; diff completo revisado. La revisión queda aprobada para el push solicitado por el usuario. La corrección agrega una lectura de refresco por adquisición de producto bloqueado; no se midió rendimiento bajo carga comercial.

## Precisión monetaria — 2026-10-02

Precio y costo validan NUMERIC(12,2) en DTO, servicio y entidad; se rechaza exceso de escala o rango sin redondear ni modificar parcialmente el producto. Regresiones cubren ambos campos, creación/actualización, límites y lectura PostgreSQL.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (9) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Respuestas atrasadas — 2026-10-02

El catálogo cancela solicitudes previas y aplica datos, paginación, carga y errores sólo si pertenecen a la solicitud vigente. El texto editado no cambia el filtro aplicado hasta buscar; las señales alcanzan todas las páginas de categorías. Chromium controla respuestas y errores tardíos y una página obsoleta.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (12) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Sesión vencida — 2026-10-02

El cliente centraliza los 401 de solicitudes protegidas en instalaciones privadas, limpia la sesión visible y vuelve al login. Tras ingresar restaura la sección anterior y conserva la recuperación de ventas sin reintentar escrituras. Un token y una versión de sesión capturados por solicitud evitan que respuestas tardías cierren una sesión nueva, incluso con el mismo token. Chromium verifica las cinco secciones y el reintento con la misma clave.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (18) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Cantidades en ventas — 2026-10-02

Las cantidades se conservan como texto editable y se validan antes de generar una clave o enviar una venta nueva; las recuperaciones mantienen su payload histórico.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (20) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Escrituras duplicadas — 2026-10-02

Los formularios de productos y categorías tienen estados y guardas de envío independientes, bloquean sus controles, conservan valores ante rechazo y distinguen creación confirmada de fallo de refresco. Chromium verifica envíos repetidos, corrección y reintento en ambos formularios.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (24) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Versiones `latest` — 2026-10-02

Las nueve dependencias directas del frontend quedan fijadas a las versiones ya resueltas en el lockfile. Se verificó instalación limpia con npm ci --offline y que el grafo transitivo, versiones e integridades no cambiaron.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (24) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Revisión de los seis hallazgos antes del push — 2026-10-02

La revisión encontró respuestas de creación de catálogo que mostraban avisos y refrescaban después de abandonar la sección. Dos regresiones de Chromium reprodujeron éxito y rechazo tardíos sobre el resumen; ambas pasan al comprobar que la vista siga montada antes de aplicar los efectos. Las operaciones ya enviadas se conservan en el servidor. Se revisaron también precisión y atomicidad monetaria, recuperación histórica, 401 de sesiones anteriores, controles y cancelación de solicitudes, y versiones exactas sin cambios transitivos.

Verificación final: 351 pruebas backend y 26 escenarios Chromium sin fallos ni omisiones, compilación frontend y `git diff --check` aprobados; diff completo revisado. Sin cambios de esquema ni dependencias. Revisión aprobada para el push explícitamente solicitado a origin/main.

## Respuestas tardías de inventario — 2026-10-02

Dos regresiones Chromium reprodujeron avisos de éxito y rechazo de ajustes sobre el resumen después de abandonar inventario. La vista ahora comprueba que siga montada antes de aplicar estado, avisos o refrescos, también al recibir la consulta posterior de stock. Los movimientos enviados conservan su resultado en el servidor; la regresión de éxito verifica el stock persistido.

Verificación: 28 escenarios Chromium aislados aprobados, compilación TypeScript/Vite y revisión visual de acceso sin errores. Suite backend completa (351), diff completo revisado y `git diff --check` aprobados. Sin cambios de esquema ni dependencias; commit local, sin push.

## Compose completo — 2026-10-03

Compose agrega la API con el Dockerfile existente y frontend Node 24/Vite con `npm ci`, ambos no root. El backend sólo agrega curl para consultar el health actual de Actuator. Se mantiene PostgreSQL 17, pg_isready y postgres_data. Interfaz y PostgreSQL se publican sólo en loopback; la API queda interna. Vite redirige `/api` a `backend:8080` y la API conecta a `database:5432`. No cambia application.properties, seguridad, migraciones, DatabaseUrlEnvironmentPostProcessor ni Render/Vercel.

Validación con proyecto aislado `stockflow-stage1`, puertos 55173/55432 y credenciales temporales fuera del repositorio, sin modificar el `.env` ni el volumen previos. El `.env` existente carece de APP_ADMIN_PASSWORD y APP_JWT_SECRET; debe completarse antes de usar el comando sin overrides. `docker compose config --quiet` pasó tanto con el ejemplo como con la configuración temporal, build pasó y up -d --wait dejó los tres servicios healthy. Flyway aplicó V1–V5 sobre una base nueva; Hibernate validó. Chromium verificó login, categoría, producto, entrada de cinco unidades, venta de dos y dashboard. Después de down sin -v y up, la venta y el stock de tres unidades persistieron; Flyway validó cinco migraciones sin reaplicar y health respondió UP. El entorno y el volumen exclusivamente de prueba se retiran al terminar, sin datos de prueba permanentes.

Pruebas específicas de autenticación/health/DATABASE_URL: seis aprobadas. Suite backend: 351 sin fallos, errores ni omisiones. Navegador aislado: 28 sin fallos, omisiones ni reintentos. Smoke adicional contra Compose: un escenario aprobado. Compilación frontend en host y en contenedor aprobada; revisión visual con agent-browser sin errores ni overlay. Diff completo revisado y git diff --check aprobado. Archivos quedan sin staging ni commit por pedido explícito.

Deuda deliberada: frontend Vite para reproducción local, rebuild al editar fuentes, imagen estática de producción separada; probes definitivas, backups, TLS cloud, usuarios de migración, métricas y despliegue siguen fuera de esta etapa. Docker requirió acceso fuera del sandbox. Un primer intento del smoke temporal eligió /tmp completo y falló al recorrer carpetas privadas del sistema; se restringió el directorio y el escenario pasó, sin cambio en la aplicación.

## Operación del backend — 2026-10-03

Etapa 2: Actuator expone sólo health, con GET anónimo para las tres rutas operativas exactas, sin componentes ni detalles. Liveness usa livenessState; readiness agrega db al estado de disponibilidad. Bootstrap adquiere un advisory lock transaccional común antes del count/insert, incluso si las instancias usan emails diferentes; conserva la unicidad existente. La regresión coordina dos inicializaciones y observa el segundo nodo esperando en pg_locks. Los errores inesperados no se ignoran y el rollback libera el lock.

RequestIdFilter, anterior a seguridad, acepta un X-Request-ID único y acotado o genera UUID; devuelve el header y contextualiza/restaura MDC en requests y redispatches. CORS agrega exclusivamente ese header. Logs locales legibles y perfil prod JSON nativo de Boot, sin dependencias nuevas. Los fallos identificables de conexión/indisponibilidad DB responden 503 DATABASE_UNAVAILABLE; errores de integridad siguen 409 y bugs inesperados 500. Se conserva diagnóstico interno.

Hikari espera 3s por conexión y 1s para validación, alineados con el healthcheck de Compose de 4s. No se cambian límites de locks, statements ni transacciones sin evidencia de carga. Boot conserva graceful shutdown nativo con fase explícita de 30s; Compose concede 40s. No hay sleeps ni hooks propios. Los tests ahora usan application-test.properties y Surefire activa test: el application.properties anterior de tests ocultaba la configuración operativa principal. Una regresión verifica que ésta realmente se cargue.

Validación final: 370 pruebas backend (19 nuevas), sin fallos, errores ni omisiones; 28 escenarios Chromium aislados sin fallos, omisiones ni reintentos; smoke comercial adicional contra Compose aprobado. Config --quiet, build y up -d --wait finales aprobados; database/backend/frontend healthy, una sola cuenta y cinco migraciones válidas, Hibernate validando. Revisión de diff y git diff --check aprobados. Se preservan cambios previos de Etapa 1 y configuración ajena; no se hizo staging, commit ni push.

Experimentos sobre stockflow-stage2 con credenciales y volumen temporales: al detener database, liveness quedó 200 UP (~0,09s), readiness 503 DOWN (~3,10s), dashboard autenticado 503 DATABASE_UNAVAILABLE (~3,02s) con request ID y respuesta sanitizada. Al restaurarla, ambas probes y dashboard volvieron a 200, sin reiniciar backend ni perder la venta. El perfil prod emitió JSON válido con requestId en el error y sin las credenciales temporales en logs. SIGTERM con una entrada de stock bloqueada permitió terminar la request HTTP 200 al liberar el lock, registró cierre ordenado de Tomcat/JPA/Hikari y salida 143 por SIGTERM, sin SIGKILL. El entorno y volumen exclusivamente de prueba se retiran al finalizar.

Problemas de validación resueltos: se actualizó una expectativa CORS que sólo admitía Location; ahora verifica también X-Request-ID. La preparación temporal inicial buscaba placeholders antiguos y dejó un JWT corto; se generaron credenciales por nombre de variable sin tocar valores del usuario. El script de apagado se ajustó al HTTP 200 real de inventario y a la salida 143 normal de SIGTERM; los logs y la respuesta confirmaron cierre correcto.

Deuda deliberada: pérdida silenciosa de paquetes, queries/locks y transacciones largas siguen sin deadline; habrá que medir y probar cancelaciones antes de fijarlos. Tampoco se dimensiona pool por réplicas, se automatiza retirada de tráfico en Compose ni se propaga MDC a futuros ejecutores. No se agregan Prometheus/Grafana, Kubernetes, Terraform, cloud, nuevas migraciones ni cambios funcionales comerciales.

## Observabilidad local — 2026-10-03

Etapa 3 agrega únicamente micrometer-registry-prometheus gestionado por Boot. El perfil observability activa management 9091 y health/prometheus, histogramas HTTP y deshabilita discovery. Compose conserva 8080 para la API; management se liga al alias backend-management de una red metrics interna compartida sólo con Prometheus. Grafana y Prometheus comparten monitoring; frontend permanece en la red de aplicación. Se verificó que frontend no conecta a backend:9091. La seguridad permite sólo los cuatro GET operativos en management y deniega el resto, también con autenticación del dominio desactivada. Sin perfil, salud y seguridad de la demo conservan su configuración previa. En Compose las probes mantienen rutas y pasan a 9091.

Prometheus v3.14.0: scrape 15s, timeout 5s, retención 7 días/1GB. Grafana 13.2.2: credenciales requeridas desde .env, sin acceso anónimo, datasource y dashboard provisionados por archivos read-only. Puertos de las UI sólo en loopback. Wget nativo realiza healthchecks sin paquetes agregados. El dashboard agrupa Overview, HTTP, JVM, proceso/CPU y Hikari; percentiles agregables desde buckets, unidades correctas y 4xx separados de fallos 5xx. No se crean métricas de dominio ni se agregan exporters/tracing/alertas.

Validación en proyecto stockflow-stage3, puertos temporales 56173/56432/59090/53000 y credenciales fuera del repositorio. Config --quiet, build, up -d --wait y promtool check config aprobados. Database/backend/frontend/prometheus/grafana healthy. Scraping real confirmado en /api/v1/targets y /targets; queries HTTP/JVM/proceso/Hikari con series almacenadas. Las 28 queries del dashboard devolvieron series. Navegador confirmó login frontend y dashboard Grafana renderizado, sin errores.

Tráfico sin escrituras de negocio: 90 lecturas, 72 HTTP 200 y 18 HTTP 404; login adicional. IDs distintos produjeron una única plantilla /api/products/{id}, sin request ID ni valores personales en tags. Percentiles directos en Prometheus tras tráfico: p50 7,84ms; p95 15,86ms; p99 69,17ms (aproximados, ventana 5m). Se observaron 69 buckets entre 1ms y 30s más +Inf. Hikari exportó active/idle/pending/max y tiempos acquire/usage/creation. Los gauges pueden mostrar active=0 al muestrear operaciones cortas entre scrapes.

PostgreSQL DOWN: liveness 200 UP, readiness 503 DOWN; 13 requests dashboard devolvieron 503 DATABASE_UNAVAILABLE, alrededor de 3,01s (las primeras 3,31s). Pool active=0, idle=0, pending hasta 2 y max=10; crecieron timeouts. Prometheus almacenó 503; p95 API subió a 3,09s. up permaneció 1, incluyendo min_over_time(up[1m])=1 durante la caída. Al restaurar PostgreSQL, readiness/liveness y dashboard volvieron a 200 sin reiniciar backend; dashboard tardó 52ms. Counters observados dependen del último scrape y pueden ir detrás de las requests terminadas.

Persistencia verificada con down sin -v y up -d --wait: una serie histórica de 503 con siete muestras conservó exactamente sus valores; una preferencia de usuario Grafana persistió y luego se restauró. Datasource/dashboard siguieron provisionados. La imagen final se reconstruyó y se utilizó al reiniciar. Un experimento separado detuvo backend: up pasó a 0 y volvió a 1 al arrancarlo; PostgreSQL DOWN mantuvo up=1. No se tocó .env ni volúmenes personales. Al finalizar se retiraron únicamente los contenedores/redes/volúmenes del proyecto temporal stockflow-stage3; el último ps registrado tenía los cinco servicios healthy.

Pruebas específicas de observabilidad: cinco nuevas, incluyendo dos listeners reales, histogramas/cardinalidad y modo sin autenticación. Suite backend final: 375 sin fallos, errores ni omisiones (370 previas + 5 nuevas); Playwright aislado: 28 aprobados, sin omisiones ni reintentos. El test BrowserE2EIT es un runner adicional fuera del cómputo de mvn test. Diff completo revisado y git diff --check aprobado. Se mantienen todos los cambios previos sin staging/commit/push.

Deuda consciente: percentiles aproximados y sensibles a poco tráfico; gauges pueden perder picos entre scrapes; recursos y autenticación local no son una política de producción. Prometheus/Grafana no tienen backups automatizados. No se mide PostgreSQL internamente ni se instrumenta readiness como métrica custom; up indica scraping, no disponibilidad comercial. Una futura plataforma debe preservar el límite de management mediante políticas de red y dimensionar retención/pool por carga. No se continúa con la próxima etapa.

## Confiabilidad y negocio — 2026-10-03

Etapa 4 implementada y verificada sobre las Etapas 1–3 sin staging/commit/push. Se analizaron ventas/idempotencia, movimientos/stock, catálogo/categorías, autenticación, dashboard y errores. Se evaluaron cuatro candidatas y se eligieron tres: stockflow_sales_confirmed_total (sin labels propios), stockflow_sales_rejected_total (reason fijo INSUFFICIENT_STOCK/INACTIVE_PRODUCT) y stockflow_stock_movements_total (type IN/OUT). Cinco series por instancia, preregistradas en cero; sin IDs, motivo libre, datos personales ni claves. Se posponen cruces de stock mínimo por semántica ambigua ante edición/reactivación. No hay métricas monetarias ni una métrica por entidad.

Los servicios programan éxitos mediante afterCommit del límite transaccional exterior; rechazo conocido se registra sólo en afterCompletion de rollback. Replay idempotente lee la venta anterior sin confirmar ni mover stock otra vez. Rollback exterior revierte también sale_confirmations. Ocho pruebas de counters incluyen commit diferido, rollback parcial/exterior, fallo posterior de persistencia, replay y labels; dos pruebas verifican readiness cacheada. Counters son telemetría best effort y no reemplazan el historial PostgreSQL. No hay migraciones ni dependencias nuevas.

Readiness se muestrea desde HealthEndpoint nativo cada 15s fuera del scrape, con gauge inicial desconocido (-1) y timestamp de chequeo como valor. No abre endpoints nuevos ni ejecuta la DB en el hilo de scraping. La cadena de seguridad y aislamiento management se conserva.

SLIs: allowlist de rutas normalizadas del dominio, 2xx/3xx éxitos y 5xx fallos; todas las 4xx, auth, health, scraping y recursos estáticos se excluyen. 503 DATABASE_UNAVAILABLE consume budget. Latencia separa GET exitosos (≤500ms) y POST /api/sales exitosos (≤1s), con buckets exactos agregados al histograma nativo. Objetivos provisionales: disponibilidad 99,5%, latencias 95%, rolling 30d. Datos parciales se identifican por cobertura, ratios sin tráfico usan -1 y no se inventa una historia mensual. Budget=0,005×elegibles, consumido=errores/budget, restante=max(0,1-consumido), burn=fracción error/0,005. Retención pasa a 32d/2GB; tamaño puede limitar historia.

Prometheus carga 50 recording rules y seis alertas versionadas. FastBurn: burn>12 en 1h/5m, ≥20 elegibles y ≥3 errores, for 2m critical. SustainedBurn: burn>4 en 6h/30m, ≥50 elegibles/≥5 errores, ≥3 errores recientes en 5m, for 10m warning. Guardias de volumen y frescura evitan un único error o una advertencia tardía después de reparar. BackendNotScrapeable for 1m critical; ReadinessDown for 2m critical; SaleLatencyDegraded for 5m warning con ≥20 éxitos y ≥3 lentas; HikariSaturated for 2m warning con pending>0 y utilización≥90%. Runbooks enlazados por annotation. No hay alerta DB duplicada ni CPU aislada.

Alertmanager 0.34.1 recibe por red Docker, tiene volumen propio, healthcheck wget nativo y puerto host sólo loopback. Receiver local-only sin integraciones externas; dos inhibiciones acotan duplicaciones. SRE Overview se provisiona junto al dashboard técnico previo: disponibilidad/cobertura, budget, latencia, dependencias/alertas y tres paneles de negocio. No se crea dashboard adicional para sólo esos tres gráficos. Las 35 consultas del nuevo dashboard produjeron datos finitos (incluyendo -1 explícito para falta de tráfico), y /api/ds/query de Grafana devolvió frames de Prometheus. Renderizado real sin errores de navegador.

Laboratorio stockflow-stage4: credenciales y volúmenes temporales, puertos 57173/57432/59990/54000/59993, sin tocar .env o datos personales. Config, build y up -d --wait aprobados; seis servicios healthy. Negocio: una categoría/producto, entrada de 30, venta de 2 y replay con misma respuesta/ID; metrics confirmed=1, IN=1, OUT=1. Dos rechazos conocidos aumentaron sólo sus razones (1 cada una), sin sumar ventas ni movimientos. Se generó tráfico moderado de lectura, no una carga agresiva.

PostgreSQL DOWN: liveness 200 UP, readiness 503 DOWN; 98 requests DB devolvieron 503 DATABASE_UNAVAILABLE. Scraping siguió up=1 durante al menos tres minutos; readiness gauge=0 y pending=2. Availability5m cayó a 26,37%, burn5m a 147,26; error budget observado se agotó en este laboratorio de volumen pequeño. FastBurn y ReadinessDown recorrieron pending→firing, recibidas en Alertmanager. SustainedBurn quedó pending y no llegó a firing. Restaurar PostgreSQL recuperó las probes/API sin reiniciar la JVM; después de tráfico sano y vencer la ventana corta, alerts de DB se retiraron en Prometheus y Alertmanager. Availability5m=100%, burn5m=0; el consumo histórico no se borró.

Latencia: tres ventas esperaron un lock de fila adquirido por psql, se observó wait_event_type=Lock antes de liberar; las tres respondieron 201 en 1,33–1,35s, sin sleep productivo ni cambios artificiales de aplicación. Prometheus almacenó 20 muestras positivas de p95 de ventas, con pico 1,414s. La alerta de latencia no disparó con sólo tres operaciones: mínimos de volumen/for funcionaron. Su disparo sostenido se verifica con promtool.

Backend DOWN: up=0; BackendNotScrapeable recorrió pending→firing y llegó a Alertmanager. Al arrancarlo, up=1 y se retiró la alerta de ambos sistemas. Probes recuperadas y snapshot final sin alertas activas. Todos los experimentos restauran su servicio en finally y se realizaron únicamente sobre el proyecto temporal.

Validación final: 385 tests backend (375 previos+10 nuevos), sin fallos/errores/omisiones; Playwright 28 aprobado sin omisiones ni reintentos. Promtool validó config/50 recording/6 alert rules y 10 escenarios deterministas (incluye budget, rutas/exclusiones, idle, mínimos, burn, latencia, scraping/recuperación, readiness, pool y guardia tras reparación); amtool validó config/ruta/receptor/inhibiciones. Targets UP, reglas health=ok, Grafana queries y provisioning verificados. Diff revisado y git diff --check aprobado. Ningún staging/commit/push.

Correcciones durante validación: se adaptó el test al descriptor concreto sellado de health de Boot 4 y al cierre explícito de SimpleMeterRegistry. La regresión matemática encontró un selector 30d que contaba éxitos como errores; se corrigió a 5xx, se revalidó y se contrastó la captura normal con counters HTTP crudos al timestamp original (35,789 elegibles estimados, 0 errores, budget 0,178945, 100% restante). La captura inicial de recording budget anterior a esa corrección se descarta; la evidencia final usa reglas corregidas. Promtool usa tolerancia sólo para el último bit flotante. Se recargaron archivos mediante SIGHUP, sin bajar umbrales/for para forzar alertas.

Deuda consciente: metas provisionales sin 30d reales ni carga comercial; SLI HTTP no ve pérdidas anteriores a la JVM ni 4xx incorrectos. Telemetría puede perder eventos por crash entre commit/callback/scrape. Gauges pierden picos, readiness cacheada necesita revisar frescura, retención size-bound puede recortar historia. No hay sondeo externo, backups automáticos ni receptor externo; runbook_url es ruta conceptual del repo. No se avanza a Kubernetes/cloud/tracing.

Cierre del laboratorio: se retiraron sólo contenedores, redes y volúmenes de stockflow-stage4, junto con sus credenciales temporales. El último docker compose ps registrado tenía los seis servicios healthy; no se tocaron volúmenes personales ni .env. Los archivos de configuración y las pruebas reproducibles permanecen en el repositorio.
