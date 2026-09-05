# Architecture

## Objetivo

StockFlow es un backend para inventario y ventas de pequeños comercios. Hoy expone operaciones HTTP para categorías, productos e inventario; el dominio y esquema inicial de ventas están preparados, pero su flujo de servicio y API todavía no existen.

## Stack

Java 21, Spring Boot 4.1.1, Maven, Spring Data JPA/Hibernate, PostgreSQL 17 y Flyway. Las pruebas usan JUnit, Mockito, MockMvc y Testcontainers con PostgreSQL real. No se usa Lombok ni H2.

## Módulos

- `category`: entidad, repositorio, servicio, API REST, DTOs, mapper y errores de categorías.
- `product`: entidad, repositorio, servicio, API REST, DTOs, mapper y errores de productos.
- `inventory`: movimientos históricos, repositorio, servicio transaccional, API REST y errores de stock.
- `sale`: migración V3, agregado histórico inmutable, repositorio paginado y servicio de confirmación. No hay API de ventas ni descuento de stock por ventas.
- `common`: `PageResponse` y `GlobalExceptionHandler`/`ApiError` compartidos.

## Flujo por capas

Las rutas HTTP reciben DTOs validados, los controllers coordinan servicios y mappers, los servicios aplican reglas/transacciones y los repositorios acceden a PostgreSQL. Las entidades JPA no se serializan directamente. `GlobalExceptionHandler` transforma errores de dominio, validación, argumentos y persistencia en `ApiError` HTTP.

## Modelo de datos actual

- `categories`: categoría con nombre único sin distinción de mayúsculas, descripción y timestamps.
- `products`: producto con SKU único, precio/costo `NUMERIC(12,2)`, stock y stock mínimo no negativos, estado activo y categoría obligatoria.
- `stock_movements`: registro inmutable de entradas/salidas, cantidad, balances, motivo y timestamp; referencia a producto.
- `sales`: total `NUMERIC(14,2)`, notas opcionales y timestamp de creación.
- `sale_items`: producto, snapshots de nombre/SKU/precio/costo, cantidad y subtotal; la migración impide productos repetidos en una venta.

Relaciones: categoría 1–N productos; producto 1–N movimientos; venta 1–N ítems; ítem N–1 producto. Las FKs usan `ON DELETE RESTRICT`; no hay borrado en cascada de historial.

## Migraciones

Flyway es la única vía de cambio de esquema y Hibernate usa `ddl-auto=validate`. Existen V1 (categorías/productos), V2 (movimientos de stock) y V3 (ventas/ítems). Las migraciones aplicadas no se editan; todo cambio requiere una V nueva con constraints e índices explícitos.

## Dinero

El dinero usa `BigDecimal`, nunca tipos binarios. Producto y precios/costos de líneas usan `NUMERIC(12,2)`; subtotales y totales usan `NUMERIC(14,2)` para soportar cantidades y acumulación de líneas. Las ventas almacenan snapshots para que futuros cambios de producto no reescriban el historial comercial.

## Inventario y concurrencia

`InventoryService` ejecuta entradas y salidas dentro de transacciones. Obtiene el producto con `PESSIMISTIC_WRITE`, valida límites/suficiencia, actualiza el stock mediante métodos de dominio y guarda `StockMovement` en la misma transacción. `StockMovement` es `@Immutable`; el historial se pagina por `created_at DESC, id DESC`.

## Pruebas

Hay pruebas unitarias de entidades, servicios, validación, mappers y controllers; pruebas JPA con `@DataJpaTest` y PostgreSQL 17/Testcontainers; pruebas de integración de servicios; y E2E HTTP con `@SpringBootTest` y MockMvc. El agregado y repositorio de ventas cuentan con pruebas unitarias y JPA.

## Decisiones y límites actuales

`SaleService` confirma únicamente agregados con ítems y verifica que `sales.total` coincida con la suma de subtotales antes de persistir. Aún no hay descuento de stock por ventas, movimientos OUT de venta, bloqueo multi-producto, API/DTOs de ventas, dashboard, frontend, autenticación, clientes, pagos, facturación ni cancelaciones. La igualdad entre `sales.total` y la suma de ítems se garantiza en el servicio transaccional, no mediante un CHECK entre tablas. Las lecturas paginadas de ventas no deben usar `JOIN FETCH` sobre ítems: la lectura detallada se resolverá posteriormente dentro de una transacción.
