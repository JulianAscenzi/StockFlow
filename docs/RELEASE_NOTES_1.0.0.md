# StockFlow v1.0.0

**Estado: candidato versionado para v1.0.0; publicación autorizada.** Backend y frontend `1.0.0`; Chart Helm `0.2.1` con `appVersion: 1.0.0`. El candidato pasó validación local el 2026-11-13. Antes de declarar la release publicada se deben verificar CI del SHA final, tag y las dos imágenes remotas.

StockFlow reúne una aplicación de inventario y ventas utilizable en un laboratorio reproducible con evidencia de consistencia, recuperación y entrega de artefactos. Es un proyecto personal; no promete operación empresarial ni SLA comercial.

## Producto y backend

- Catálogo de categorías/productos, entradas/salidas de inventario, confirmación e historial de ventas, búsqueda paginada y resumen diario.
- Backend Java 21/Spring Boot modular con contratos DTO, PostgreSQL 17/Flyway y dinero BigDecimal.
- Venta, stock e historial en una transacción; locking ordenado, snapshots e idempotencia persistente.
- Ajustes manuales idempotentes con recuperación en la interfaz tras respuesta perdida, navegación o recarga.
- Cierre de auditoría: estados/headers HTTP de Spring preservados y catálogo paginado con ID como desempate.
- Autenticación de administrador BCrypt/JWT para instalaciones privadas y recuperación de sesión en frontend React/TypeScript.

## Testing y reproducción

Validación local repetida con las versiones de release: **406 tests backend y 35 escenarios Playwright**, todos aprobados el 2026-11-13, más build frontend, lint Helm y doce tests de scripts/Chart aprobados. Las pruebas usan PostgreSQL real/Testcontainers y cubren contratos MVC, rollback, concurrencia y flujos de navegador aislados. Compose inicia aplicación y observabilidad con configuración externa, imágenes no root y persistencia local. [Guía local](LOCAL_DEVELOPMENT.md).

## Observabilidad y SRE

Liveness/readiness, graceful shutdown, request ID y logs JSON; Micrometer/Prometheus, Grafana y Alertmanager provisionados. Métricas de negocio después del commit, SLOs provisionales, cobertura de historia, error budget y burn-rate alerts con runbooks. Los counters son telemetría y no sustituyen el historial comercial. [SRE](SRE.md).

## Kubernetes y Helm

Laboratorio kind con dos réplicas backend, startup probes y PostgreSQL StatefulSet/PVC. Experimentos registrados de self-healing, caída/recuperación DB, graceful shutdown, fallo de liveness, rolling deployment, rollback y persistencia. Chart Helm propio con Secrets externos, configuración compartida y upgrade/rollback/uninstall/reinstall verificados. Kind de un nodo y DB única no ofrecen HA completa. [Kubernetes](KUBERNETES.md) · [Helm](HELM.md).

## CI y supply chain

GitHub Actions valida backend, frontend y navegador; construye OCI con SBOM SPDX/provenance SLSA v1, prueba el artefacto exacto y lo publica en GHCR sin reconstruir. Tags SHA completo, digest remoto verificado y consumo por digest desde kind registrados. Las attestations no equivalen a firma independiente ni certificación SLSA; la publicación de los dos paquetes no es atómica. [Supply chain](CONTAINER_REGISTRY.md).

## Terraform y diseño AWS

ADR comparando ECS, EKS y EC2, estimaciones de costos y Terraform para ECS Fargate/ALB/RDS con networking, IAM, secrets externos y controles de costo. Validación estática y siete tests mock. **Sin plan real, apply ni recursos AWS provisionados**: mantener el diseño sin infraestructura permanente evita costos continuos de portfolio. Prerrequisitos productivos y runtime cloud permanecen explícitos. [ADR](adr/0001-cloud-runtime.md) · [Terraform](../terraform/README.md).

## Límites conocidos

Frontend Vite en contenedor para laboratorio; demo gratuita con datos ficticios y persistencia limitada. Comprobación del 2026-11-13: frontend HTTP 200, API sin respuesta tras dos intentos de 45 segundos; la demo completa no está validada y se recomienda la reproducción local. Sin backups automatizados/restore local, NetworkPolicy efectiva, TLS de laboratorio, scanner de vulnerabilidades, firma independiente ni evidencia de 30 días de SLO. AWS diseñado no representa operación cloud. Estas extensiones están separadas como [Optional future work](ROADMAP.md#optional-future-work).

## Release readiness

| Criterio | Estado comprobado | Paso antes de publicar |
| --- | --- | --- |
| Auditoría funcional | Tres hallazgos corregidos: inventario `031b8d6`, HTTP `f827289`, paginación `3699a59` | Mantener sus regresiones en CI. |
| Tests locales | 406 backend / 35 navegador y build frontend aprobados con las versiones de release | Confirmar CI del SHA final, incluido el cierre documental. |
| CI e imágenes | La evidencia GHCR remota de STATUS sigue siendo histórica; no se hizo push de estos cambios | Autorizar push y comprobar tests, smoke, publicación y ambos digests del candidato. |
| Árbol y docs | Correcciones funcionales en commits separados; README, presentación y este borrador actualizados | Confirmar árbol limpio tras el commit documental y revisar sus enlaces. |
| Secrets | Revisión de los diffs locales y capturas sin credenciales reales expuestas | Revisar también alertas GitHub Security autenticadas; no se accedió a ellas. |
| Versionado | Backend/frontend 1.0.0; Chart 0.2.1 / appVersion 1.0.0; dependencias sin cambios | Verificar estas versiones en el SHA que se etiquete. |
| Capturas | Resumen, historial y detalle actualizados con datos ficticios en instancia aislada | Usar el contexto/commit registrados en el [inventario de imágenes](images/README.md); no atribuirlas a la demo pública. |
| Infraestructura | Compose/kind/GHCR con evidencia histórica; AWS diseñado sin provisionar | No se requiere desplegar AWS para presentar el portfolio. |
| Licencia | MIT existente | Mantener atribución. |

La publicación está autorizada. Antes de anunciar v1.0.0, comprobar CI del SHA final, tag semántico y los dos digests publicados. La validación local no sustituye estas comprobaciones remotas.
