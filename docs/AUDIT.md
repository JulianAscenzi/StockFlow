# Auditoría general — 2026-10-02

Revisión del backend, frontend, migraciones, autenticación, configuración y CI a partir de `981aee2`. Se revisó código y se usó PostgreSQL 17 mediante Testcontainers. No se auditó infraestructura remota ni se modificó la demo pública.

## Bugs corregidos

| Prioridad | Problema | Evidencia y solución |
| --- | --- | --- |
| Media | Creaciones del catálogo responden después de salir de la sección | Dos regresiones Chromium reprodujeron avisos tardíos de éxito y rechazo sobre el resumen. Productos y categorías ahora comprueban que la vista siga montada antes de aplicar estado, avisar o refrescar; las solicitudes ya enviadas conservan su resultado en el servidor. |
| Baja | Versiones `latest` | Las nueve dependencias directas del frontend quedan fijadas a las versiones ya resueltas en el lockfile. Se verificó instalación limpia con npm ci --offline y que el grafo transitivo, versiones e integridades no cambiaron. |
| Baja | Escrituras duplicadas | Los formularios de productos y categorías tienen estados y guardas de envío independientes, bloquean sus controles, conservan valores ante rechazo y distinguen creación confirmada de fallo de refresco. Chromium verifica envíos repetidos, corrección y reintento en ambos formularios. |
| Baja | Cantidades en ventas | Las cantidades se conservan como texto editable y se validan antes de generar una clave o enviar una venta nueva; las recuperaciones mantienen su payload histórico. |
| Media | Sesión vencida | El cliente centraliza los 401 de solicitudes protegidas en instalaciones privadas, limpia la sesión visible y vuelve al login. Tras ingresar restaura la sección anterior y conserva la recuperación de ventas sin reintentar escrituras. Un token y una versión de sesión capturados por solicitud evitan que respuestas tardías cierren una sesión nueva, incluso con el mismo token. Chromium verifica las cinco secciones y el reintento con la misma clave. |
| Media | Respuestas atrasadas | El catálogo cancela solicitudes previas y aplica datos, paginación, carga y errores sólo si pertenecen a la solicitud vigente. El texto editado no cambia el filtro aplicado hasta buscar; las señales alcanzan todas las páginas de categorías. Chromium controla respuestas y errores tardíos y una página obsoleta. |
| Media | Precisión monetaria | Precio y costo validan NUMERIC(12,2) en DTO, servicio y entidad; se rechaza exceso de escala o rango sin redondear ni modificar parcialmente el producto. Regresiones cubren ambos campos, creación/actualización, límites y lectura PostgreSQL. |
| Alta | Estado activo obsoleto en la sesión JPA pese al bloqueo | La revisión posterior reprodujo dos rechazos ausentes: tanto ventas por IDs como agregados preparados aceptaban un producto cargado antes de una desactivación concurrente confirmada. La lectura bloqueada ahora refresca la entidad, con flush previo para conservar cambios propios. Se verifica rechazo sin venta ni movimientos, estado inactivo conservado y operaciones sucesivas de inventario/estado/venta en una misma transacción. |
| Media | Nuevas ventas de productos inactivos | Confirmación HTTP y agregados preparados validan el estado actual bajo `PESSIMISTIC_WRITE`, en orden de ID, antes de descontar stock. Responden 409 `PRODUCT_INACTIVE` y revierten ventas, stock, movimientos y clave. La recuperación de ventas confirmadas conserva snapshots; reactivar permite reutilizar una clave rechazada. La interfaz conserva el carrito editable y limpia únicamente la recuperación correspondiente. |
| Alta | Editar, activar o desactivar un producto restaura stock anterior cuando coincide con un movimiento | Tres regresiones concurrentes fallaron: un egreso dejaba 7 unidades pero la escritura del catálogo restauraba 10. `ProductService` ahora obtiene `PESSIMISTIC_WRITE` antes de leer el producto. Se verifica también que el historial conserve el balance 10 → 7. |
| Alta | La confirmación usada por HTTP puede vender usando stock obsoleto de la sesión JPA | La nueva regresión aceptaba vender 8 unidades después de un egreso concurrente que dejaba sólo 7. `SaleService.confirm(notes, lines)` ahora bloquea en orden ascendente de ID antes de leer productos y crear snapshots. La operación insuficiente se revierte sin movimientos adicionales. Las pruebas previas de ventas concurrentes usaban agregados armados fuera de la transacción y no cubrían este recorrido. |
| Media | La primera respuesta de una venta y su recuperación idempotente tienen fechas diferentes | La suite falló porque `Instant.now()` devolvía nanosegundos y PostgreSQL redondeaba a microsegundos. La fecha de creación de `Sale` ahora se trunca a microsegundos antes de persistir. La regresión compara las respuestas completas y comprueba la precisión inicial. |
| Media | CORS bloquea activar/desactivar productos desde otro origen | Los endpoints usan PATCH, pero CORS sólo admitía GET/POST/PUT/DELETE/OPTIONS. Se agrega PATCH y se verifica el preflight de ambos endpoints desde un origen permitido. |

Las regresiones concurrentes coordinan transacciones mediante latches y observan esperas reales en PostgreSQL. Usan Futures con timeout y no emplean `Thread.sleep`.

## Hallazgos pendientes y mejoras

Los seis hallazgos pendientes quedaron corregidos y verificados.

## Fortalezas verificadas en código

- Flyway controla el esquema; Hibernate valida y open-in-view está deshabilitado.
- Dinero backend en BigDecimal; snapshots históricos, FKs restrictivas y constraints explícitos.
- Movimientos y descuentos comparten transacción; adquisición de bloqueos ordenada para ventas.
- Dashboard separa agregados de ventas e ítems y usa una lectura REPEATABLE_READ.
- Controllers devuelven DTOs; los mappers consultan relaciones necesarias y no serializan entidades.
- Autenticación habilitada por defecto para instalaciones privadas; modo público de portfolio documentado.
- CI contempla backend, compilación frontend y navegador, con comprobación de pruebas sin omisiones.

## Alcance de validación

Los resultados finales de esta ejecución se registran en [STATUS](STATUS.md). La revisión no equivale a un pentest ni valida el rendimiento con un volumen comercial. Los seis hallazgos restantes se corrigieron con las verificaciones específicas registradas arriba y en STATUS.
