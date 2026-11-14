# StockFlow v1.0.0

**Publicada el 2026-11-14:** [StockFlow v1.0.0](https://github.com/JulianAscenzi/StockFlow/releases/tag/v1.0.0), tag anotado sobre `8cfd8c7a7e8e9592bf4232e58a8cbda6d02c3eac`. Backend/frontend `1.0.0`; Chart `0.2.1`, `appVersion: 1.0.0`. CI de main y del tag aprobadas; ambos digests y attestations remotos verificados.

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

## Evidencia publicada

- [CI de main](https://github.com/JulianAscenzi/StockFlow/actions/runs/43548090690) y [CI de v1.0.0](https://github.com/JulianAscenzi/StockFlow/actions/runs/43548667219): ambas aprobadas para `8cfd8c7`.
- Informes descargados del tag: **406 pruebas backend**, cero fallos/errores/omisiones; **35 escenarios Playwright**, cero fallos/omisiones/flaky. Build, smoke del OCI exacto y publicación de ambos componentes aprobados.
- Verificación independiente de GHCR: tags `1.0.0`, índices por digest, labels de commit/versión, SBOM/provenance y sus subjects; pull Docker anónimo por digest de ambos componentes aprobado.

| Imagen | Digest OCI de v1.0.0 |
| --- | --- |
| `ghcr.io/julianascenzi/stockflow-backend:1.0.0` | `sha256:6e30830aaede1156593d3b04b0d7df38a36ea41b6a7e0d56074c2ee47de7e935` |
| `ghcr.io/julianascenzi/stockflow-frontend:1.0.0` | `sha256:2ba86fbfa64928709d2e98b6c0cd42c4e78910f5d19a8d3bd317a72f2a1da2a5` |

La [release](https://github.com/JulianAscenzi/StockFlow/releases/tag/v1.0.0) conserva **13 assets**: tres capturas, Chart `stockflow-0.2.1.tgz`, dos metadata publicadas, dos SBOM, dos provenance, verificación de registry, resumen de tests y `SHA256SUMS`. Los trece archivos se contrastaron por nombre, tamaño y SHA-256 con la API de GitHub antes de publicar. No se subieron OCI tars, credenciales ni informes runtime completos.

El tag permanece fijo sobre el commit de release. El cierre documental posterior registra resultados sin cambiar ese tag ni reconstruir sus imágenes. Las capturas conservan su contexto original; no representan la demo pública. Las alertas autenticadas de GitHub Security no se inspeccionaron y esta publicación no acredita ausencia de vulnerabilidades.
