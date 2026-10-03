# Auditoría general — 2026-10-02

Revisión del backend, frontend, migraciones, autenticación, configuración y CI a partir de `981aee2`. Se revisó código y se usó PostgreSQL 17 mediante Testcontainers. No se auditó infraestructura remota ni se modificó la demo pública.

## Bugs corregidos

| Prioridad | Problema | Evidencia y solución |
| --- | --- | --- |
| Alta | Editar, activar o desactivar un producto restaura stock anterior cuando coincide con un movimiento | Tres regresiones concurrentes fallaron: un egreso dejaba 7 unidades pero la escritura del catálogo restauraba 10. `ProductService` ahora obtiene `PESSIMISTIC_WRITE` antes de leer el producto. Se verifica también que el historial conserve el balance 10 → 7. |
| Alta | La confirmación usada por HTTP puede vender usando stock obsoleto de la sesión JPA | La nueva regresión aceptaba vender 8 unidades después de un egreso concurrente que dejaba sólo 7. `SaleService.confirm(notes, lines)` ahora bloquea en orden ascendente de ID antes de leer productos y crear snapshots. La operación insuficiente se revierte sin movimientos adicionales. Las pruebas previas de ventas concurrentes usaban agregados armados fuera de la transacción y no cubrían este recorrido. |
| Media | La primera respuesta de una venta y su recuperación idempotente tienen fechas diferentes | La suite falló porque `Instant.now()` devolvía nanosegundos y PostgreSQL redondeaba a microsegundos. La fecha de creación de `Sale` ahora se trunca a microsegundos antes de persistir. La regresión compara las respuestas completas y comprueba la precisión inicial. |
| Media | CORS bloquea activar/desactivar productos desde otro origen | Los endpoints usan PATCH, pero CORS sólo admitía GET/POST/PUT/DELETE/OPTIONS. Se agrega PATCH y se verifica el preflight de ambos endpoints desde un origen permitido. |

Las regresiones concurrentes coordinan transacciones mediante latches y observan esperas reales en PostgreSQL. Usan Futures con timeout y no emplean `Thread.sleep`.

## Hallazgos pendientes y mejoras

1. **Media — Estado inactivo sin defensa en confirmación.** `SaleService` no verifica `Product.active`; una solicitud directa o un carrito anterior a la desactivación puede vender el producto. El selector sí filtra activos. Conviene decidir explícitamente si desactivar impide nuevas ventas o sólo oculta el producto del selector; si impide ventas, validar bajo bloqueo y devolver un conflicto de dominio. Una recuperación idempotente de una venta ya confirmada debe seguir funcionando.
2. **Media — Precisión monetaria sin validar en productos.** `ProductCreateRequest`, `ProductUpdateRequest` y `Product` validan no negatividad pero no los límites de `NUMERIC(12,2)`. Un precio con tres decimales puede redondearse en PostgreSQL y diferir de la respuesta inicial; valores fuera de rango llegan a persistencia. Agregar validación de diez dígitos enteros y dos fraccionarios, también para llamadas de servicio, con regresiones HTTP y PostgreSQL. Las líneas de venta ya validan precisión mediante `MonetaryValues`.
3. **Media — Respuestas atrasadas en catálogo.** `ProductsView.refresh` aplica todas las respuestas sin cancelación ni identificador de solicitud. Dos búsquedas o páginas concurrentes pueden dejar visible un resultado anterior. Aplicar el patrón de descarte que ya usan el selector y el historial, con una prueba de navegador que controle el orden de respuestas.
4. **Media — Sesión vencida fuera de ventas.** `App` considera autenticado al usuario por la presencia del token; un 401 en catálogo/resumen/inventario no dispara el login. Actualmente puede salir y volver a entrar manualmente. Centralizar el tratamiento de 401 conservando la recuperación de ventas, con una regresión de expiración.
5. **Baja — Cantidades en ventas.** La confirmación se ejecuta con un botón fuera de un formulario validado. `min` y `max` no impiden enviar decimales o cantidades superiores al stock mostrado. Validar enteros positivos antes de enviar; el stock final debe seguir verificándose en el servidor porque el mostrado puede quedar desactualizado.
6. **Baja — Escrituras duplicadas del catálogo.** Crear productos/categorías no bloquea controles mientras la solicitud está en curso. La unicidad protege los datos, pero se generan solicitudes repetidas y mensajes confusos. Agregar estado de envío y distinguir errores de refresco posteriores a una creación confirmada.
7. **Baja — Versiones `latest`.** `frontend/package.json` usa `latest` para dependencias principales. El lockfile y `npm ci` mantienen instalaciones reproducibles; regenerar el lockfile puede incorporar saltos mayores. Fijar versiones y actualizar de manera deliberada.

## Fortalezas verificadas en código

- Flyway controla el esquema; Hibernate valida y open-in-view está deshabilitado.
- Dinero backend en BigDecimal; snapshots históricos, FKs restrictivas y constraints explícitos.
- Movimientos y descuentos comparten transacción; adquisición de bloqueos ordenada para ventas.
- Dashboard separa agregados de ventas e ítems y usa una lectura REPEATABLE_READ.
- Controllers devuelven DTOs; los mappers consultan relaciones necesarias y no serializan entidades.
- Autenticación habilitada por defecto para instalaciones privadas; modo público de portfolio documentado.
- CI contempla backend, compilación frontend y navegador, con comprobación de pruebas sin omisiones.

## Alcance de validación

Los resultados finales de esta ejecución se registran en [STATUS](STATUS.md). La revisión no equivale a un pentest ni valida el rendimiento con un volumen comercial. Los hallazgos pendientes provienen de inspección de código; no se presentan como reproducciones ejecutadas.
