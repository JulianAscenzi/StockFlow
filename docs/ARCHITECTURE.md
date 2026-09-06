# Architecture

## Objetivo

StockFlow es un backend para inventario y ventas de pequeños comercios. Expone operaciones HTTP para categorías, productos, inventario, confirmación de ventas y dashboard diario.

## Stack

Java 21, Spring Boot 4.1.1, Maven, Spring Data JPA/Hibernate, PostgreSQL 17 y Flyway. Las pruebas usan JUnit, Mockito, MockMvc y Testcontainers con PostgreSQL real. No se usa Lombok ni H2.

## Módulos

- `category`: entidad, repositorio, servicio, API REST, DTOs, mapper y errores de categorías.
- `product`: entidad, repositorio, servicio, API REST, DTOs, mapper y errores de productos.
- `inventory`: movimientos históricos, repositorio, servicio transaccional, API REST y errores de stock.
- `sale`: migración V3, agregado histórico inmutable, repositorio paginado, servicio de confirmación transaccional, DTOs, mapper y controller de confirmación.
- `common`: `PageResponse` y `GlobalExceptionHandler`/`ApiError` compartidos.
- `dashboard`: consultas agregadas, servicio de lectura y `GET /api/dashboard`.

## Dashboard diario

`GET /api/dashboard?page=0&size=20` devuelve fecha y zona `America/Argentina/Buenos_Aires`, cantidad de ventas, facturación, unidades vendidas, margen bruto estimado y productos con bajo stock paginados. El día usa inicio inclusivo y medianoche siguiente exclusiva.

La facturación y cantidad se agregan sobre ventas; unidades y margen se agregan por separado sobre los snapshots de las líneas. Esto evita multiplicar totales al unir ventas con ítems, incluso si varias ventas tienen el mismo total. El margen es subtotal menos costo histórico por cantidad, admite pérdidas y no representa ganancia neta ni cobros.

Bajo stock significa `stock <= minimumStock` para todos los productos, incluidos inactivos. Se filtra en PostgreSQL antes de paginar, con orden fijo por stock, nombre e ID y metadatos de página. Los agregados diarios no dependen de esa página. Las lecturas del servicio comparten una transacción read-only `REPEATABLE_READ`. No se modifican esquemas ni se cargan colecciones de ítems para calcular métricas.

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

`SaleService` confirma agregados con ítems, verifica que `sales.total` coincida con la suma de subtotales y ordena las líneas por `productId` antes de descontar cada una mediante `InventoryService` en la misma transacción. Así los bloqueos pesimistas de productos se adquieren en orden determinista y se evitan deadlocks entre ventas con líneas invertidas; las ventas concurrentes no pueden sobrepasar el stock disponible. Cada descuento registra un movimiento `OUT` con motivo `Sale`; una falta de stock revierte venta, stock y movimientos. `GlobalExceptionHandler` expone `EMPTY_SALE` (400), `PRODUCT_NOT_FOUND` (404) e `INSUFFICIENT_STOCK` (409) sin detalles internos. `POST /api/sales` valida el contrato, construye la venta desde IDs de producto y devuelve `201 Created` con su detalle histórico. La igualdad entre `sales.total` y la suma de ítems se garantiza en el servicio transaccional, no mediante un CHECK entre tablas. Las lecturas paginadas de ventas no deben usar `JOIN FETCH` sobre ítems: la lectura detallada se resolverá posteriormente dentro de una transacción.
