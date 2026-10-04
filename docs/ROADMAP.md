# Roadmap

Estado reconstruido desde commits, código y pruebas. `[x]` significa entregado en commit; `[ ]` pendiente.

1. [x] Base, categorías y productos — objetivo: CRUD y esquema inicial. Módulos: `category`, `product`, V1. Aceptación: API, validación, unicidad y PostgreSQL verificados. Pruebas: unitarias, JPA, MVC y E2E. Dependencias: ninguna.
2. [x] Inventario y movimientos — objetivo: entradas/salidas transaccionales con historial. Módulo: `inventory`, V2. Aceptación: bloqueo pesimista, balances y errores de stock. Pruebas: unitarias, JPA, concurrencia e HTTP. Dependencias: productos.
3. [x] Esquema de ventas — objetivo: historial de ventas e ítems. Archivo: V3. Aceptación: snapshots, FKs restrictivas, checks e índices. Pruebas: PostgreSQL/Flyway. Dependencias: productos.
4. [x] Entidades y repositorio de ventas — objetivo: `Sale`/`SaleItem` inmutables y `SaleRepository`. Módulo: `sale`. Aceptación: agregado, snapshots, precisión, colección segura, persistencia y paginación. Pruebas: `SaleTest` y `SaleRepositoryTest` PostgreSQL. Dependencias: V3.
5. [x] Implementar SaleService — objetivo: confirmar una venta armada y validar reglas de aplicación. Módulo: `sale`. Aceptación: no persiste ventas vacías y mantiene total consistente. Pruebas: unitarias/transaccionales. Dependencias: bloque 4.
6. [x] Integrar venta y descuento transaccional de stock — objetivo: venta y stock como una transacción. Módulos: `sale`, `inventory`, `product`. Aceptación: todo confirma o revierte junto. Pruebas: integración PostgreSQL. Dependencias: bloque 5.
7. [x] Evitar deadlocks — objetivo: bloquear productos de una venta en orden determinista. Módulos: `sale`, `product`. Aceptación: orden por ID antes de `PESSIMISTIC_WRITE`. Pruebas: concurrencia determinista. Dependencias: bloque 6.
8. [x] Registrar movimientos OUT — objetivo: un `StockMovement` OUT por línea vendida. Módulos: `sale`, `inventory`. Aceptación: balances/motivos/historial correctos. Pruebas: integración. Dependencias: bloque 6.
9. [x] Rollback por stock insuficiente — objetivo: no dejar venta, stock ni movimientos parciales. Módulos: `sale`, `inventory`. Aceptación: rollback total. Pruebas: integración. Dependencias: bloques 6–8.
10. [x] Ventas concurrentes — objetivo: impedir sobreventa. Módulos: `sale`, `product`. Aceptación: resultados deterministas sin sobrepasar stock. Pruebas: Futures con timeout. Dependencias: bloques 7–9.
11. [x] DTOs y mappers de ventas — objetivo: contratos HTTP sin exponer entidades. Módulo: `sale/api`. Aceptación: requests/responses y mapeo de detalle. Pruebas: validación/mappers. Dependencias: bloque 5.
12. [x] Errores HTTP de ventas — objetivo: códigos públicos para venta/producto/stock inválidos. Módulos: `sale`, `common/error`. Aceptación: respuestas coherentes de conflicto/validación. Pruebas: advice/MVC. Dependencias: bloques 5–10.
13. [x] SaleController — objetivo: endpoint de confirmación y consultas necesarias. Módulo: `sale/api`. Aceptación: controller delgado y DTOs validados. Pruebas: MVC. Dependencias: bloques 11–12.
14. [x] Pruebas MVC de ventas — objetivo: contratos HTTP de ventas. Módulo: `sale/api`. Aceptación: estados, payloads y errores. Pruebas: `@WebMvcTest`. Dependencias: bloque 13.
15. [x] Pruebas E2E de ventas — objetivo: flujo HTTP → PostgreSQL completo. Módulos: `sale`, `inventory`. Aceptación: snapshots, descuentos, movimientos y rollback visibles. Pruebas: MockMvc/Testcontainers. Dependencias: bloques 6–14.
16. [x] Consultas mínimas de dashboard — facturación y cantidad de ventas, unidades vendidas, margen bruto histórico del día argentino y productos con stock bajo paginados. Módulos: `dashboard`, `sale`, `product`. Pruebas: servicio, MVC y PostgreSQL/E2E. Dependencias: bloque 15 y métricas aprobadas.
17. [x] Cerrar backend MVP — objetivo: revisar alcance y calidad del backend. Módulos: todos. Aceptación: migraciones, API y pruebas completas. Pruebas: suite total y revisión manual. Dependencias: bloques 4–16.
18. [x] Construir frontend — objetivo: interfaz utilizable por comercio. Módulo: nuevo frontend React + TypeScript + Vite. Aceptación: resumen, catálogo de productos/categorías, ajustes de inventario y ventas. Pruebas: compilación TypeScript/Vite. Dependencias: bloque 17 y decisión tecnológica.
19. [x] Integrar frontend y backend — objetivo: flujo real de inventario/ventas. Módulos: frontend y API. Aceptación: contratos y errores consumidos correctamente. Pruebas: E2E aislado con categoría, producto, entrada, venta y dashboard. Dependencias: bloque 18.
20. [x] Autenticación de administrador — objetivo: un único acceso seguro sin ampliar el producto. Módulos: `auth`, frontend y V4. Aceptación: BCrypt, JWT con expiración, API protegida y login. Pruebas: integración con PostgreSQL. Dependencias: decisión explícita.
21. [x] Documentar ejecución y despliegue — objetivo: operación reproducible. Archivos: README/docs/compose. Aceptación: guía local verificada, límites de producción documentados. Pruebas: arranque limpio. Dependencias: bloque 17.
22. [x] Publicar demo de portfolio — objetivo: una demostración pública, reproducible y segura para datos ficticios. Módulos: infraestructura/documentación. Aceptación: interfaz Vercel, API y PostgreSQL Render Free, CORS explícito, health check, sin secretos versionados y límites de la demo documentados. Pruebas: smoke manual de categoría, producto, entrada, venta y dashboard. Dependencias: bloques 19 y 21. Decisión: el producto se presenta como portfolio; no se autorizan datos comerciales ni infraestructura de producción.
23. [x] Revisión final del MVP de portfolio — objetivo: aceptación final del alcance acordado. Módulos: todos. Aceptación: flujo público verificable, autenticación disponible para una futura instalación privada, documentación de límites, compilación de interfaz y suite backend aprobadas. Pruebas: suite total y E2E. Dependencias: bloque 22.

## Ampliaciones posteriores al MVP

El MVP permanece cerrado. Orden aprobado: pruebas de navegador → CI → historial → búsqueda → idempotencia.

24. [x] Pruebas de navegador aisladas — Chromium, Spring Boot con autenticación y PostgreSQL 17 temporal; recorrido comercial, controles bloqueados, conflictos de stock y resumen actualizado.
25. [x] Integración continua — jobs backend, compilación frontend y navegador; sin despliegues.
26. [x] Historial y detalle de ventas — consultas paginadas y snapshots históricos en la interfaz.
27. [x] Búsqueda paginada compartida — nombre/SKU, filtros de venta e inventario y selección conservada.
28. [x] Confirmación idempotente — clave UUID persistida transaccionalmente y recuperación en la misma pestaña mediante sessionStorage. La recuperación ignora respuestas tardías de operaciones anteriores.

## Evolución DevOps — ejecución local

29. [x] Entorno completo con Docker Compose — PostgreSQL 17 persistente, backend existente y frontend Vite no root; networking interno, puertos host en loopback, health checks y configuración documentada. Implementación y validación registradas en STATUS e incluidas en df830d7. No incluye observabilidad, Kubernetes, Terraform ni cloud.
30. [x] Operación del backend — probes separadas, bootstrap concurrente seguro, graceful shutdown, request ID/logs y presupuesto del pool. Implementación y validación registradas en STATUS e incluidas en df830d7. No incluye Prometheus/Grafana ni Kubernetes.

31. [x] Observabilidad local — Etapa 3: Micrometer/Prometheus/Grafana, histogramas HTTP, dashboard provisionado y persistencia. Implementación y evidencia en STATUS e incluidas en df830d7. Excluye negocio, alertas, SLOs, tracing y cloud.

32. [x] Confiabilidad y negocio — Etapa 4: counters transaccionales, SLIs/SLOs provisionales, budget, recording rules, alertas y Alertmanager local, dashboard SRE y runbooks. Implementación/evidencia en STATUS e incluidas en df830d7. No incluye Kubernetes, cloud ni tracing.

33. [x] Kubernetes local — Etapa 5: kind, manifiestos declarativos, dos réplicas, probes, PostgreSQL/PVC, recursos, secrets, observabilidad multiinstancia y experimentos reales de recuperación, apagado, rollout/rollback. Implementación incluida en d395a63; evidencia en STATUS y KUBERNETES. No incluye Helm, Terraform, cloud, operadores ni autoscaling.

34. [ ] Helm — Etapa 6: Chart propio equivalente a Kubernetes, values acotados, Secrets externos, observabilidad canónica compartida, install/upgrade/rollback/uninstall y preservación de PVCs. Implementada y validada localmente; pendiente de commit por instrucción explícita (sin staging/commit/push). Evidencia en STATUS y HELM. No incluye Terraform, cloud, GitOps ni operadores.
