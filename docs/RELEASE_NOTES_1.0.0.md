# StockFlow v1.0.0 — borrador de release

**Estado: preparado para revisión, no publicado.** No existe un tag v1.0.0 creado por este cierre. La versión propuesta corresponde al alcance del proyecto de portfolio; los componentes conservan sus versiones actuales hasta una autorización de release.

StockFlow reúne una aplicación de inventario y ventas utilizable en un laboratorio reproducible con evidencia de consistencia, recuperación y entrega de artefactos. Es un proyecto personal; no promete operación empresarial ni SLA comercial.

## Producto y backend

- Catálogo de categorías/productos, entradas/salidas de inventario, confirmación e historial de ventas, búsqueda paginada y resumen diario.
- Backend Java 21/Spring Boot modular con contratos DTO, PostgreSQL 17/Flyway y dinero BigDecimal.
- Venta, stock e historial en una transacción; locking ordenado, snapshots e idempotencia persistente.
- Autenticación de administrador BCrypt/JWT para instalaciones privadas y recuperación de sesión en frontend React/TypeScript.

## Testing y reproducción

Evidencia registrada de **385 tests backend y 28 escenarios Playwright**, con PostgreSQL real/Testcontainers, contratos MVC, rollback, concurrencia y flujos de navegador aislados. Compose inicia aplicación y observabilidad con configuración externa, imágenes no root y persistencia local. [Guía local](LOCAL_DEVELOPMENT.md).

## Observabilidad y SRE

Liveness/readiness, graceful shutdown, request ID y logs JSON; Micrometer/Prometheus, Grafana y Alertmanager provisionados. Métricas de negocio después del commit, SLOs provisionales, cobertura de historia, error budget y burn-rate alerts con runbooks. Los counters son telemetría y no sustituyen el historial comercial. [SRE](SRE.md).

## Kubernetes y Helm

Laboratorio kind con dos réplicas backend, startup probes y PostgreSQL StatefulSet/PVC. Experimentos registrados de self-healing, caída/recuperación DB, graceful shutdown, fallo de liveness, rolling deployment, rollback y persistencia. Chart Helm propio con Secrets externos, configuración compartida y upgrade/rollback/uninstall/reinstall verificados. Kind de un nodo y DB única no ofrecen HA completa. [Kubernetes](KUBERNETES.md) · [Helm](HELM.md).

## CI y supply chain

GitHub Actions valida backend, frontend y navegador; construye OCI con SBOM SPDX/provenance SLSA v1, prueba el artefacto exacto y lo publica en GHCR sin reconstruir. Tags SHA completo, digest remoto verificado y consumo por digest desde kind registrados. Las attestations no equivalen a firma independiente ni certificación SLSA; la publicación de los dos paquetes no es atómica. [Supply chain](CONTAINER_REGISTRY.md).

## Terraform y diseño AWS

ADR comparando ECS, EKS y EC2, estimaciones de costos y Terraform para ECS Fargate/ALB/RDS con networking, IAM, secrets externos y controles de costo. Validación estática y siete tests mock. **Sin plan real, apply ni recursos AWS provisionados**: mantener el diseño sin infraestructura permanente evita costos continuos de portfolio. Prerrequisitos productivos y runtime cloud permanecen explícitos. [ADR](adr/0001-cloud-runtime.md) · [Terraform](../terraform/README.md).

## Límites conocidos

Frontend Vite en contenedor para laboratorio; demo gratuita con datos ficticios y persistencia limitada. Sin backups automatizados/restore local, NetworkPolicy efectiva, TLS de laboratorio, scanner de vulnerabilidades, firma independiente ni evidencia de 30 días de SLO. AWS diseñado no representa operación cloud. Estas extensiones están separadas como [Optional future work](ROADMAP.md#optional-future-work).

## Release readiness

| Criterio | Estado al preparar el borrador | Acción antes de publicar |
| --- | --- | --- |
| Working tree limpio | No: cambios previos y este cierre documental sin staging | Revisar cambios y autorizar commit; confirmar árbol limpio después. |
| Tests verdes | Checkpoints: 385 backend / 28 navegador; sin cambios funcionales en este cierre | Verificar suite/CI del commit que se etiquetará; no presentar reports antiguos como nueva ejecución. |
| CI verde | Run remoto registrado de 5063c4b, no del candidato v1.0.0 | Confirmar CI del SHA final, smoke y publicación de ambas imágenes. |
| Docs actualizadas | README, estado, mapa, roadmap y material de portfolio preparados | Revisar diff final, enlaces y cualquier corrección posterior. |
| Secrets auditados | Revisión estática del contenido publicable sin credenciales reales detectadas | Revisar staged diff autorizado y alertas GitHub Security autenticadas; no se accedió a ellas en este cierre. |
| Versionado coherente | Backend 0.0.1-SNAPSHOT, frontend 0.1.0, Chart 0.2.0 / appVersion 0.0.1-SNAPSHOT | Autorizar versión backend/frontend de release y appVersion correspondiente; Chart tiene ciclo propio. No simular versión 1.0.0 ahora. |
| Roadmap | Completed y Optional future work separados | Confirmar alcance; AWS desplegado no es requisito. |
| Release notes | Este borrador preparado | Revisar texto y vincular evidencia del candidato. |
| Capturas | Imagen real del producto y diagramas; checklist de material adicional | Capturar manualmente si se desea ampliar presentación. |
| Licencia | MIT existente | Mantener atribución; sin cambio de licencia. |

La publicación requiere autorización explícita posterior para staging/commit, versionado, tag y release. No ejecutar esos pasos como parte del cierre documental. Después de etiquetar con autorización, verificar el flujo semver real y los dos digests antes de afirmar que la release está publicada.
