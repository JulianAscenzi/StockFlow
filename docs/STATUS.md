# Status

## Completed modules

El backend MVP está cerrado: `category`, `product`, `inventory`, `common`, `sale` y `dashboard` están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

MVP de portfolio terminado. `auth` conserva un único administrador inicial configurable por entorno, contraseñas BCrypt, JWT HS256 de ocho horas y una pantalla de inicio de sesión para instalaciones privadas. La demo gratuita de portfolio permanece pública con autenticación desactivada intencionalmente y datos ficticios; en ese modo no se crean los componentes ni la ruta JWT y no se requiere `APP_JWT_SECRET`. El catálogo de productos se pagina en la interfaz y el selector de categorías carga todas sus páginas; ventas e inventario consultan una página por vez mediante búsqueda por nombre/SKU. La confirmación de venta bloquea sus controles mientras la solicitud está en curso.

## Deployment decision

El alcance aprobado es una demostración pública para CV: Vercel sirve la interfaz y Render Free la API con PostgreSQL. No se autorizan datos comerciales ni se contrata infraestructura de producción. La guía de despliegue explica la persistencia limitada, falta de backups y el smoke test de la demo.

## Last general test result

Verificación del 2026-10-02: **351 pruebas backend sin fallos, errores ni omisiones**, compilación frontend y **28 escenarios de navegador aislado** aprobados. Los checkpoints de auditoría se registran abajo.

## Cuaderno del proyecto

La carpeta `docs` puede abrirse como bóveda de Obsidian desde [Inicio](Inicio.md). Incluye una decisión sobre bloqueo de stock y un guion de demostración, con enlaces Markdown y configuración personal excluida de Git. El cambio documental se valida mediante revisión de enlaces locales y `git diff --check`; por autorización explícita del usuario no se ejecuta Maven para este cambio, dado que Docker no está disponible. El resultado general anterior corresponde a la última ejecución registrada, no a esta actualización documental.

## Pending decisions

Ninguna para el MVP de portfolio. Un uso comercial futuro requerirá decidir proveedor, backups, operación y secretos propios antes de cargar información real.

## Real blockers

Ninguno dentro del alcance de portfolio.

## Ampliaciones en curso

Docker es accesible con ejecución fuera del sandbox. La verificación inicial detectó una regresión previa: el advice convertía rutas inexistentes en 500; se corrige a 404 `RESOURCE_NOT_FOUND`, con regresión para login deshabilitado.

Verificación del 2026-10-01: regresión específica y suite backend completa aprobadas (315 pruebas).

Bloque 24 completado: Playwright aislado, cinco escenarios aprobados, compilación frontend aprobada y 315 pruebas backend aprobadas. El resumen vuelve a consultar al navegar y descarta respuestas obsoletas. Próximo bloque: CI.

Bloque 25: workflow CI con jobs backend, frontend y navegador dependiente de ambos. Ubuntu 24.04, Java 21, Node 24, Docker obligatorio, comprobación de pruebas ejecutadas sin omisiones e informes por siete días. La primera ejecución remota queda pendiente de que el usuario publique la rama; no se hizo push.

Bloque 26 completado: historial paginado y detalle histórico, sin alterar inventario. Pruebas específicas: 13; suite completa: 319; navegador: seis escenarios; compilación y whitespace aprobados. Próximo bloque: búsqueda paginada.

Bloque 27 completado: búsqueda paginada compartida, filtros PostgreSQL y selección conservada. Pruebas específicas: 66; suite completa: 322; navegador: ocho escenarios; compilación y whitespace aprobados. Próximo bloque: confirmación idempotente.

Bloque 28: confirmación idempotente implementada. La recuperación usa una clave UUID persistida en `sessionStorage`; las respuestas tardías sólo pueden limpiar la operación que las originó, evitando borrar una recuperación nueva tras navegar. La compilación frontend pasa. Las pruebas de integración con Docker/Testcontainers quedaron verificadas durante la auditoría del 2026-10-02.

## Auditoría general — 2026-10-02

Docker/PostgreSQL disponibles. La auditoría detectó una diferencia entre la fecha inicial de una venta (nanosegundos) y su recuperación idempotente (microsegundos de PostgreSQL). Se corrige la fecha de creación a precisión de microsegundos y se refuerza la regresión de igualdad de respuestas. La validación conjunta final pasó 54 pruebas específicas y 336 pruebas backend completas sin fallos, errores ni omisiones; la compilación frontend también pasó.

CORS ahora admite PATCH para activar/desactivar productos desde un origen permitido; se verifica el preflight de ambas rutas. Esta corrección está incluida en la validación conjunta de 54 pruebas específicas y 336 pruebas completas.

Se reprodujeron y corrigieron dos fallas de stock: editar/activar/desactivar sobrescribía stock concurrente; la confirmación utilizada por HTTP podía vender con stock obsoleto de la sesión JPA. El catálogo ahora bloquea antes de leer, y las ventas bloquean los productos en orden de ID antes de crear snapshots. Se agregan cuatro escenarios concurrentes deterministas y una prueba del orden de bloqueos. El [informe de auditoría](AUDIT.md) documenta cuatro bugs corregidos, siete hallazgos pendientes y sus prioridades. No se modificaron migraciones ni se hizo push.

## Productos inactivos y ventas — 2026-10-02

Primer hallazgo pendiente de la auditoría corregido: desactivar impide nuevas ventas. `SaleService` valida el estado actual bajo bloqueo pesimista en orden de ID para solicitudes HTTP y ventas preparadas, antes de descontar stock. El advice responde 409 `PRODUCT_INACTIVE`. La recuperación idempotente de una venta confirmada sigue devolviendo el historial; una clave rechazada puede reutilizarse tras reactivar. Los ajustes de inventario de inactivos permanecen permitidos. La interfaz muestra el mensaje de rechazo, conserva el carrito editable y limpia sólo la recuperación correspondiente.

Validación: 26 pruebas específicas; suite completa de 342 pruebas sin fallos, errores ni omisiones; compilación TypeScript/Vite; nueve escenarios de Chromium aislado aprobados. Las regresiones concurrentes observan la espera real de PostgreSQL y verifican ambos órdenes de venta/desactivación. Diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias; sin push.

## Revisión previa a publicación — 2026-10-02

La revisión de los cuatro fixes pendientes de publicación encontró una falla adicional: adquirir `PESSIMISTIC_WRITE` no refrescaba productos ya cargados en la sesión JPA. Dos regresiones PostgreSQL reprodujeron ventas aceptadas después de una desactivación concurrente confirmada, tanto por IDs como mediante agregados preparados. La lectura bloqueada se implementa en un fragmento del repositorio, compartido por ventas, inventario y catálogo: flush de cambios propios, bloqueo y refresh. Una tercera regresión verifica balances y estado al encadenar cambios de estado, inventario y venta dentro de una sola transacción. No cambia contratos HTTP, esquema ni dependencias.

Verificación final de la revisión: 45 pruebas específicas, 345 pruebas backend completas y nueve escenarios Chromium aislados aprobados, sin fallos ni omisiones. Compilación frontend y `git diff --check` aprobados; diff completo revisado. La revisión queda aprobada para el push solicitado por el usuario. La corrección agrega una lectura de refresco por adquisición de producto bloqueado; no se midió rendimiento bajo carga comercial.

## Precisión monetaria — 2026-10-02

Precio y costo validan NUMERIC(12,2) en DTO, servicio y entidad; se rechaza exceso de escala o rango sin redondear ni modificar parcialmente el producto. Regresiones cubren ambos campos, creación/actualización, límites y lectura PostgreSQL.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (9) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Respuestas atrasadas — 2026-10-02

El catálogo cancela solicitudes previas y aplica datos, paginación, carga y errores sólo si pertenecen a la solicitud vigente. El texto editado no cambia el filtro aplicado hasta buscar; las señales alcanzan todas las páginas de categorías. Chromium controla respuestas y errores tardíos y una página obsoleta.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (12) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Sesión vencida — 2026-10-02

El cliente centraliza los 401 de solicitudes protegidas en instalaciones privadas, limpia la sesión visible y vuelve al login. Tras ingresar restaura la sección anterior y conserva la recuperación de ventas sin reintentar escrituras. Un token y una versión de sesión capturados por solicitud evitan que respuestas tardías cierren una sesión nueva, incluso con el mismo token. Chromium verifica las cinco secciones y el reintento con la misma clave.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (18) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Cantidades en ventas — 2026-10-02

Las cantidades se conservan como texto editable y se validan antes de generar una clave o enviar una venta nueva; las recuperaciones mantienen su payload histórico.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (20) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Escrituras duplicadas — 2026-10-02

Los formularios de productos y categorías tienen estados y guardas de envío independientes, bloquean sus controles, conservan valores ante rechazo y distinguen creación confirmada de fallo de refresco. Chromium verifica envíos repetidos, corrección y reintento en ambos formularios.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (24) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Versiones `latest` — 2026-10-02

Las nueve dependencias directas del frontend quedan fijadas a las versiones ya resueltas en el lockfile. Se verificó instalación limpia con npm ci --offline y que el grafo transitivo, versiones e integridades no cambiaron.

Verificación: pruebas específicas, suite completa (351), compilación frontend y navegador aislado (24) aprobados; diff completo revisado y `git diff --check` aprobado. Sin cambios de esquema ni dependencias nuevas; commit local, sin push.

## Revisión de los seis hallazgos antes del push — 2026-10-02

La revisión encontró respuestas de creación de catálogo que mostraban avisos y refrescaban después de abandonar la sección. Dos regresiones de Chromium reprodujeron éxito y rechazo tardíos sobre el resumen; ambas pasan al comprobar que la vista siga montada antes de aplicar los efectos. Las operaciones ya enviadas se conservan en el servidor. Se revisaron también precisión y atomicidad monetaria, recuperación histórica, 401 de sesiones anteriores, controles y cancelación de solicitudes, y versiones exactas sin cambios transitivos.

Verificación final: 351 pruebas backend y 26 escenarios Chromium sin fallos ni omisiones, compilación frontend y `git diff --check` aprobados; diff completo revisado. Sin cambios de esquema ni dependencias. Revisión aprobada para el push explícitamente solicitado a origin/main.

## Respuestas tardías de inventario — 2026-10-02

Dos regresiones Chromium reprodujeron avisos de éxito y rechazo de ajustes sobre el resumen después de abandonar inventario. La vista ahora comprueba que siga montada antes de aplicar estado, avisos o refrescos, también al recibir la consulta posterior de stock. Los movimientos enviados conservan su resultado en el servidor; la regresión de éxito verifica el stock persistido.

Verificación: 28 escenarios Chromium aislados aprobados, compilación TypeScript/Vite y revisión visual de acceso sin errores. Suite backend completa (351), diff completo revisado y `git diff --check` aprobados. Sin cambios de esquema ni dependencias; commit local, sin push.
