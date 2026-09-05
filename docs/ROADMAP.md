# Roadmap

Estado reconstruido desde commits, código y pruebas. `[x]` significa entregado en commit; `[ ]` pendiente.

1. [x] Base, categorías y productos — objetivo: CRUD y esquema inicial. Módulos: `category`, `product`, V1. Aceptación: API, validación, unicidad y PostgreSQL verificados. Pruebas: unitarias, JPA, MVC y E2E. Dependencias: ninguna.
2. [x] Inventario y movimientos — objetivo: entradas/salidas transaccionales con historial. Módulo: `inventory`, V2. Aceptación: bloqueo pesimista, balances y errores de stock. Pruebas: unitarias, JPA, concurrencia e HTTP. Dependencias: productos.
3. [x] Esquema de ventas — objetivo: historial de ventas e ítems. Archivo: V3. Aceptación: snapshots, FKs restrictivas, checks e índices. Pruebas: PostgreSQL/Flyway. Dependencias: productos.
4. [x] Entidades y repositorio de ventas — objetivo: `Sale`/`SaleItem` inmutables y `SaleRepository`. Módulo: `sale`. Aceptación: agregado, snapshots, precisión, colección segura, persistencia y paginación. Pruebas: `SaleTest` y `SaleRepositoryTest` PostgreSQL. Dependencias: V3.
5. [ ] Implementar SaleService — objetivo: confirmar una venta armada y validar reglas de aplicación. Módulo: `sale`. Aceptación: no persiste ventas vacías y mantiene total consistente. Pruebas: unitarias/transaccionales. Dependencias: bloque 4.
6. [ ] Integrar venta y descuento transaccional de stock — objetivo: venta y stock como una transacción. Módulos: `sale`, `inventory`, `product`. Aceptación: todo confirma o revierte junto. Pruebas: integración PostgreSQL. Dependencias: bloque 5.
7. [ ] Evitar deadlocks — objetivo: bloquear productos de una venta en orden determinista. Módulos: `sale`, `product`. Aceptación: orden por ID antes de `PESSIMISTIC_WRITE`. Pruebas: concurrencia determinista. Dependencias: bloque 6.
8. [ ] Registrar movimientos OUT — objetivo: un `StockMovement` OUT por línea vendida. Módulos: `sale`, `inventory`. Aceptación: balances/motivos/historial correctos. Pruebas: integración. Dependencias: bloque 6.
9. [ ] Rollback por stock insuficiente — objetivo: no dejar venta, stock ni movimientos parciales. Módulos: `sale`, `inventory`. Aceptación: rollback total. Pruebas: integración. Dependencias: bloques 6–8.
10. [ ] Ventas concurrentes — objetivo: impedir sobreventa. Módulos: `sale`, `product`. Aceptación: resultados deterministas sin sobrepasar stock. Pruebas: Futures con timeout. Dependencias: bloques 7–9.
11. [ ] DTOs y mappers de ventas — objetivo: contratos HTTP sin exponer entidades. Módulo: `sale/api`. Aceptación: requests/responses y mapeo de detalle. Pruebas: validación/mappers. Dependencias: bloque 5.
12. [ ] Errores HTTP de ventas — objetivo: códigos públicos para venta/producto/stock inválidos. Módulos: `sale`, `common/error`. Aceptación: respuestas coherentes de conflicto/validación. Pruebas: advice/MVC. Dependencias: bloques 5–10.
13. [ ] SaleController — objetivo: endpoint de confirmación y consultas necesarias. Módulo: `sale/api`. Aceptación: controller delgado y DTOs validados. Pruebas: MVC. Dependencias: bloques 11–12.
14. [ ] Pruebas MVC de ventas — objetivo: contratos HTTP de ventas. Módulo: `sale/api`. Aceptación: estados, payloads y errores. Pruebas: `@WebMvcTest`. Dependencias: bloque 13.
15. [ ] Pruebas E2E de ventas — objetivo: flujo HTTP → PostgreSQL completo. Módulos: `sale`, `inventory`. Aceptación: snapshots, descuentos, movimientos y rollback visibles. Pruebas: MockMvc/Testcontainers. Dependencias: bloques 6–14.
16. [ ] Consultas mínimas de dashboard — objetivo: métricas operativas acordadas. Módulos probables: `sale`, `inventory`. Aceptación: consultas paginables/eficientes y contratos definidos. Pruebas: JPA/servicio. Dependencias: bloque 15 y decisión de métricas.
17. [ ] Cerrar backend MVP — objetivo: revisar alcance y calidad del backend. Módulos: todos. Aceptación: migraciones, API y pruebas completas. Pruebas: suite total y revisión manual. Dependencias: bloques 4–16.
18. [ ] Construir frontend — objetivo: interfaz utilizable por comercio. Módulo: nuevo frontend. Aceptación: flujos MVP definidos. Pruebas: según stack elegido. Dependencias: bloque 17 y decisión tecnológica.
19. [ ] Integrar frontend y backend — objetivo: flujo real de inventario/ventas. Módulos: frontend y API. Aceptación: contratos y errores consumidos correctamente. Pruebas: E2E. Dependencias: bloque 18.
20. [ ] Autenticación cuando corresponda — objetivo: acceso acordado, no anticipado. Módulos: por definir. Aceptación: alcance/producto decidido. Pruebas: seguridad e integración. Dependencias: decisión explícita.
21. [ ] Documentar ejecución y despliegue — objetivo: operación reproducible. Archivos: README/docs/compose. Aceptación: guía verificada. Pruebas: arranque limpio. Dependencias: bloque 17.
22. [ ] Preparar despliegue de producción — objetivo: configuración y operación seguras. Módulos: infraestructura/documentación. Aceptación: configuración sin secretos y checklist de despliegue aprobado. Pruebas: smoke. Dependencias: bloques 19 y 21.
23. [ ] Revisión final del MVP — objetivo: aceptación final del alcance acordado. Módulos: todos. Aceptación: checklist funcional y de calidad aprobado. Pruebas: suite total y E2E. Dependencias: bloque 22.
