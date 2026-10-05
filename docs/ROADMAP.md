# Roadmap

StockFlow está completo dentro de su alcance de proyecto personal de portfolio. El cierre incluye producto, backend, frontend, operación local, supply chain y diseño cloud validado sin provisionamiento AWS. Las entregas y sus resultados históricos están en [STATUS](STATUS.md); la preparación de release está en [RELEASE_NOTES_1.0.0](RELEASE_NOTES_1.0.0.md).

## Completed

- [x] Backend MVP modular: categorías, productos, inventario, ventas y dashboard diario.
- [x] PostgreSQL 17/Flyway V1–V5, constraints, dinero BigDecimal e historial con snapshots.
- [x] Ventas, stock y movimientos transaccionales; rollback completo, bloqueo pesimista ordenado y pruebas de concurrencia.
- [x] Frontend React/TypeScript: catálogo, inventario, ventas, historial, búsqueda paginada y dashboard.
- [x] Administrador inicial BCrypt/JWT, recuperación de sesión y confirmación idempotente persistente.
- [x] Auditoría funcional y regresiones de estado JPA obsoleto, stock concurrente, precisión monetaria y respuestas tardías de UI.
- [x] Testing por capas con PostgreSQL real/Testcontainers y Playwright aislado; evidencia de 385 tests backend y 28 escenarios de navegador.
- [x] Demo Vercel/Render con datos ficticios y límites documentados; reproducción local independiente de su disponibilidad.
- [x] Docker/Compose: imágenes no root, persistencia, red interna y configuración externa.
- [x] Operación: liveness/readiness, graceful shutdown, request ID, logs JSON y recuperación DB.
- [x] Observabilidad: Micrometer, Prometheus, Grafana y dashboards provisionados.
- [x] SRE: métricas transaccionales, SLI/SLO provisionales, cobertura, error budget, burn-rate alerts, Alertmanager y runbooks.
- [x] Kubernetes kind: dos réplicas, startup probes, StatefulSet/PVC y experimentos reales de self-healing, DB outage, graceful shutdown, liveness, rollout/rollback y persistencia.
- [x] Helm: Chart propio, Secrets externos, configuración compartida, upgrade/rollback y conservación de PVCs verificados.
- [x] CI/GHCR: tests, build OCI, smoke del artefacto exacto, publicación SHA, SBOM/provenance y consumo por digest desde kind. Validación remota registrada en el checkpoint 18de2c0.
- [x] Cloud Architecture / IaC: ADR ECS/EKS/EC2, costos evaluados, Terraform ECS Fargate/ALB/RDS implementado, validación estática y siete tests mock. Implementación en 4367a2d; sin plan real ni recursos AWS.

El cierre documental de portfolio se prepara en el working tree: README, mapa documental, CV/entrevista, checklist de capturas y borrador de release. No implica un commit, tag ni release publicados.

## Optional future work

Estas mejoras no son requisitos para considerar StockFlow completo como portfolio y no autorizan trabajo adicional automáticamente:

- Despliegue AWS temporal, con presupuesto y permisos explícitos; validación de cuenta/plan, bootstrap DB y experimentos runtime cloud.
- Un vulnerability scanner con política de excepciones; firma independiente y archivo durable de evidencia de releases.
- NetworkPolicy con CNI que la aplique, TLS, deadlines medidos y sondeo externo del recorrido completo.
- Backups automatizados y restauración probada; estrategia de migraciones compatibles y recuperación de datos.
- HPA, cluster multinodo/HA, sizing por carga y revisión del presupuesto de conexiones.
- Tracing distribuido, propagación de contexto y notificaciones operativas externas.
- CI/CD cloud, imágenes multi-arch y runtime frontend estático en contenedor si se necesita fuera de Vercel.

AWS permanece diseñado y sin provisionar deliberadamente para evitar costos continuos. Los límites productivos están documentados, no ocultos como tareas obligatorias del portfolio. Usuarios adicionales, clientes, pagos, cancelaciones y facturación requieren una decisión de producto futura.
