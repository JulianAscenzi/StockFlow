# StockFlow — presentación profesional

Proyecto personal de portfolio, construido para demostrar backend engineering, confiabilidad y decisiones de plataforma con evidencia reproducible. No se presenta como experiencia de operación empresarial ni como servicio corriendo en AWS. El [README](../README.md) presenta el producto; [STATUS](STATUS.md) respalda resultados y límites.

## Versión corta — CV

- Built a personal inventory and sales platform with Java 21/Spring Boot, React/TypeScript and PostgreSQL; implemented transactional stock updates, concurrency control and persistent sale idempotency.
- Validated the application with 385 backend tests and 28 Playwright scenarios; built Docker/Helm labs with observability, SLO/error-budget alerts and documented recovery experiments.
- Implemented a tested OCI-to-GHCR pipeline with SBOM/provenance and digest-based deployment; designed ECS Fargate/ALB/RDS infrastructure in Terraform with static validation and mock tests, intentionally without AWS provisioning.

Las cantidades corresponden a los checkpoints de STATUS. Antes de enviar el CV, actualizar sólo si existe nueva evidencia y comprobar la disponibilidad de cualquier demo que se enlace.

## Versión LinkedIn

StockFlow es mi proyecto personal de portfolio: una aplicación de inventario y ventas para pequeños comercios con Java 21/Spring Boot, React/TypeScript y PostgreSQL. Trabajé la consistencia entre ventas, stock e historial con transacciones, bloqueos ordenados e idempotencia persistente, respaldada por 385 tests backend y 28 escenarios Playwright registrados. Extendí el proyecto con Docker, Kubernetes local y Helm, observabilidad con Prometheus/Grafana, SLOs provisionales, error budget y experimentos documentados de recuperación y despliegue. La CI prueba el artefacto OCI antes de publicarlo en GHCR con SBOM/provenance y permite desplegar por digest. También comparé ECS, EKS y EC2, evalué costos y definí el diseño ECS Fargate/ALB/RDS en Terraform con validación estática y tests mock. AWS permanece sin provisionar deliberadamente para evitar costos continuos; la evidencia operativa corresponde a laboratorios locales y a CI/GHCR remotos.

## Versión entrevista — 60–90 segundos

“StockFlow es un proyecto personal que construí alrededor de un problema concreto: mantener consistente el inventario de un comercio cuando registra entradas, salidas y ventas. Tiene una interfaz React con catálogo, historial y resumen diario, y un backend modular Spring Boot con PostgreSQL y Flyway.

La decisión principal fue conservar un monolito y una transacción para venta, stock, movimientos y confirmación idempotente. Bloqueo los productos en orden y refresco su estado bajo el lock para evitar sobreventa y lecturas obsoletas. Las ventas guardan snapshots; una repetición con la misma clave recupera el resultado sin descontar otra vez.

Además de los tests por capas y los recorridos Playwright, trabajé la operación: separé liveness de readiness, instrumenté métricas y logs, y definí SLOs provisionales con error budget. En kind probé caída de PostgreSQL, recuperación de pods, graceful shutdown, rolling deployment y rollback. Son resultados de laboratorio, no un SLA comercial.

La CI prueba las imágenes antes de publicarlas en GHCR con SBOM y provenance; Helm consume el digest. Para cloud comparé ECS, EKS y EC2 y diseñé ECS Fargate con ALB y RDS en Terraform. Lo validé estáticamente con tests mock y decidí no provisionar AWS permanentemente para evitar costos innecesarios.”

Ensayar el ritmo; usar el [guion de producto](portfolio/Guion%20de%20demostracion.md) para la demostración posterior y el [checklist de capturas](images/README.md) como apoyo visual.

## Interview talking points

| Pregunta | Guía de respuesta |
| --- | --- |
| ¿Por qué monolito y no microservicios? | Un dominio pequeño, un desarrollador y una transacción comercial compartida. Package-by-feature separa responsabilidades sin introducir red, consistencia eventual ni operación de varios servicios. Extraería un servicio por necesidad medida. |
| ¿Por qué ECS y no EKS? | Kubernetes ya está demostrado en kind. ECS Fargate reduce operación de hosts/control plane y aporta servicios administrados distintos. No es la opción más barata en todos los casos; el ADR compara también EC2 y sus responsabilidades. |
| ¿Por qué no desplegaste AWS permanentemente? | El objetivo es evidencia de diseño y reproducibilidad, con costo controlado. Terraform, ADR y estimaciones existen; no confundo tests mock con runtime AWS. Un despliegue temporal requeriría presupuesto, permisos y resolver HTTPS/bootstrap DB. |
| ¿Liveness vs readiness vs startup? | Liveness indica si reiniciar puede ayudar; readiness determina elegibilidad para tráfico e incluye DB. Startup da tiempo al arranque antes de liveness. Una DB caída no debe reiniciar todas las JVM sanas. |
| ¿Cómo manejaste concurrencia? | PESSIMISTIC_WRITE por producto; orden ascendente para ventas con varias líneas. Flush y refresh bajo lock evitan que una entidad JPA ya cargada use stock obsoleto. Integración PostgreSQL coordina esperas reales y Futures con timeout. |
| ¿Cómo funciona la idempotencia? | Header UUID opcional, fingerprint SHA-256 de notas/líneas normalizadas y registro persistido con la venta. Advisory try-lock transaccional coordina la clave; solicitud en curso o payload distinto produce conflicto. Replay devuelve snapshots previos. Sin clave no hay deduplicación. |
| ¿Qué pasa si la respuesta se pierde después del commit? | No reintentar una escritura como operación nueva. Conservar clave y payload original permite recuperar esa venta. Una clave nueva sería una nueva venta válida; idempotencia no deduplica intenciones arbitrarias. |
| ¿Qué significa un error budget? | Para el SLO provisional 99,5%, se permite 0,5% de errores entre requests elegibles observadas. Burn rate es la fracción de error dividida por 0,005. Dos ventanas y mínimos de volumen evitan avisos por errores aislados; no hay 30 días certificados. |
| ¿Qué excluye el SLI? | Auth, probes, scraping y 4xx; cuenta 5xx de rutas comerciales normalizadas. No observa requests perdidas antes de la JVM ni 4xx incorrectos. Scraping/readiness y tests cubren parte de esos límites; sonda externa sería una mejora. |
| ¿Por qué usar digest? | Un tag puede cambiar; el digest identifica el índice OCI exacto probado, incluidos sus manifiestos/attestations. CI verifica el digest remoto y Helm consume repository@digest. No afirma reproducibilidad bit a bit de otro build. |
| ¿SBOM y provenance equivalen a seguridad garantizada? | SBOM enumera componentes; provenance describe el build y su fuente. No sustituyen scanner, firma independiente ni revisión de permisos. Publicar dos paquetes tampoco es atómico: comprobar ambos digests antes de promover. |
| ¿Helm rollback vs kubectl rollout undo? | Helm recupera una revisión de la release y crea otra revisión; undo sólo recupera el PodTemplate del Deployment. No mezclar gestores. Ninguno revierte migraciones, datos ni Secrets externos. |
| ¿Qué ocurre si PostgreSQL cae? | Tras el arranque, liveness y scraping permanecen UP; readiness baja. En Compose la API devuelve 503 sanitizado; Kubernetes retira endpoints. Restaurar DB recuperó las conexiones sin reiniciar JVM. Un arranque frío sin DB sí puede fallar. |
| ¿Cómo evitaste alta cardinalidad? | Rutas MVC normalizadas y labels de negocio enumerados reason/type. Sin IDs, emails, SKU, request ID ni texto libre en métricas. Pod/instance son etiquetas de infraestructura con churn acotado al laboratorio. |
| ¿Por qué métricas después del commit? | Evita contar éxito de una transacción revertida o una recuperación idempotente. Son best effort: un crash antes del callback/scrape puede perder telemetría; el historial SQL es la fuente de verdad. |
| ¿Cómo evitarías pérdida de datos? | PVC conserva datos ante reemplazo de pod, no ante pérdida del cluster. Faltan backups automatizados y restore probado local. El diseño RDS incluye cifrado, backups, protección y snapshot final, sin validación runtime. Definir RPO/RTO, restaurar y probar migraciones antes de uso real. |
| ¿Dos réplicas significan alta disponibilidad? | Reducen impacto del fallo de un pod; kind sigue en un nodo y DB única. PDB protege eviction voluntaria, no fallo del nodo ni delete pod. No presento ese laboratorio como HA de extremo a extremo. |
| ¿Qué sacrificarías antes de sumar herramientas? | Priorizaría backup/restore, capacidad y un SLI del recorrido real. La stack existente ya permite explicar producto, integridad, entrega y diagnóstico; más plataformas no resuelven por sí solas esas garantías. |

## Evidencia para mostrar

- [Arquitectura](ARCHITECTURE.md) y [bloqueo de stock](decisiones/Bloqueo%20de%20stock.md): modelo y regresiones.
- [SRE](SRE.md), [runbooks](RUNBOOK.md) y [experimentos Kubernetes](KUBERNETES.md#resultados-medidos-2026-10-03): matemática, comportamiento y límites.
- [Supply chain](CONTAINER_REGISTRY.md): publicación del artefacto exacto y vínculo SHA/digest.
- [ADR cloud](adr/0001-cloud-runtime.md), [Terraform](../terraform/README.md) y [costos](AWS_COSTS.md): diseño sin provisionamiento.
