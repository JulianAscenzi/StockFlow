# Architecture

## Objetivo

StockFlow es un backend para inventario y ventas de pequeños comercios. Expone operaciones HTTP para categorías, productos, inventario, confirmación de ventas y dashboard diario.

## Stack

Java 21, Spring Boot 4.1.1, Maven, Spring Data JPA/Hibernate, PostgreSQL 17 y Flyway. Las pruebas usan JUnit, Mockito, MockMvc y Testcontainers con PostgreSQL real. No se usa Lombok ni H2. El frontend usa versiones exactas de sus dependencias directas y `npm ci` con el lockfile; las actualizaciones de versiones se realizan deliberadamente.

## Módulos

- `category`: entidad, repositorio, servicio, API REST, DTOs, mapper y errores de categorías.
- `product`: entidad, repositorio, servicio, API REST, DTOs, mapper y errores de productos.
- `inventory`: movimientos históricos, repositorio, servicio transaccional, API REST y errores de stock.
- `sale`: migración V3, agregado histórico inmutable, repositorio paginado, servicio de confirmación transaccional, DTOs, mapper y controller de confirmación, listado paginado y detalle histórico.
- `auth`: administrador inicial, BCrypt, JWT HS256 y filtro de seguridad stateless.
- `common`: `PageResponse` y `GlobalExceptionHandler`/`ApiError` compartidos.
- `dashboard`: consultas agregadas, servicio de lectura y `GET /api/dashboard`.
- `frontend`: cliente React + TypeScript + Vite con resumen, catálogo de productos/categorías, ajustes de inventario y confirmación de ventas. Durante desarrollo, Vite redirige `/api` al backend local. Las creaciones de productos y categorías bloquean su propio formulario mientras se envían; conservan los valores si se rechazan y distinguen una creación confirmada de un fallo posterior de actualización del catálogo. Después de abandonar la vista, las respuestas de creación no aplican estado, avisos ni refrescos.

## Ejecución local con Compose

El backend incluye probes separadas (liveness sólo estado del proceso, readiness estado más DB), bootstrap de administrador serializado en PostgreSQL, request ID mediante MDC, perfil `prod` JSON y cierre ordenado nativo de Boot. La configuración, sus límites y el experimento de recuperación están en [OPERATIONS](OPERATIONS.md). Fuera del perfil local de observabilidad sólo health se expone por Actuator. Compose agrega Micrometer/Prometheus/Grafana: management 9091 ligado a una red interna de métricas, datasource y dashboard provisionados. [OBSERVABILITY](OBSERVABILITY.md) detalla seguridad y flujo.

`docker compose up --build` inicia Prometheus, Grafana, PostgreSQL 17, la API con su Dockerfile multietapa existente y el frontend con Node 24/Vite. API y frontend ejecutan como usuarios no root. El navegador accede al frontend en loopback, puerto 5173 configurable con `FRONTEND_PORT`; Vite redirige `/api` a `backend:8080`. La API usa `database:5432`, sin puerto publicado. PostgreSQL conserva `postgres_data` y publica su puerto sólo en loopback para permitir también el desarrollo con Maven.

El arranque espera `pg_isready` y después `/actuator/health`; el frontend comprueba HTTP con Node. Son health checks locales, no la estrategia definitiva de probes. Las credenciales se inyectan explícitamente desde `.env`, excluido de Git y de los contextos Docker. Compose deriva el login frontend del mismo `APP_AUTH_ENABLED` de la API. El código frontend se copia en la imagen y requiere rebuild al editar; esta imagen Vite es para reproducción local, mientras Vercel conserva su compilación estática.

## Despliegue de demo pública

La demo pública de portfolio usa Vercel para la interfaz estática y Render Free para la API Dockerizada y PostgreSQL 17. La interfaz recibe `VITE_API_BASE_URL` en compilación y la API sólo habilita CORS para los orígenes explícitos de `APP_CORS_ALLOWED_ORIGINS`; ninguna de esas variables es un secreto. Render provee su cadena interna `DATABASE_URL`; un `EnvironmentPostProcessor` la adapta a propiedades JDBC antes de iniciar JPA/Flyway. El endpoint operativo `GET /actuator/health` no revela detalles y se usa como health check. La demo fija `APP_AUTH_ENABLED=false` y sólo contiene datos ficticios.

La infraestructura de demo se declara en `render.yaml`, sin credenciales, y la guía `docs/DEPLOYMENT.md` especifica sus límites y smoke test. Render Free suspende la API inactiva y elimina la base a los 30 días; es adecuado sólo para información ficticia. La aplicación sí incluye autenticación, pero la demo la desactiva y no representa controles de acceso de producción.

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
- `sale_confirmations`: clave UUID de idempotencia, hash de la solicitud, venta confirmada y timestamp; impide reutilizar una clave para otra operación.
- `sale_items`: producto, snapshots de nombre/SKU/precio/costo, cantidad y subtotal; la migración impide productos repetidos en una venta.
- `application_users`: una cuenta administradora inicial con email único sin distinción de mayúsculas y hash BCrypt.

Relaciones: categoría 1–N productos; producto 1–N movimientos; venta 1–N ítems; ítem N–1 producto. Las FKs usan `ON DELETE RESTRICT`; no hay borrado en cascada de historial.

## Migraciones

Flyway es la única vía de cambio de esquema y Hibernate usa `ddl-auto=validate`. Existen V1 (categorías/productos), V2 (movimientos de stock), V3 (ventas/ítems) y V4 (usuarios de aplicación). Las migraciones aplicadas no se editan; todo cambio requiere una V nueva con constraints e índices explícitos.

La migración V5 agrega `sale_confirmations` con una clave primaria UUID, hash de solicitud, relación única con `sales` y FK restrictiva.

## Dinero

El dinero usa `BigDecimal`, nunca tipos binarios. Producto y precios/costos de líneas usan `NUMERIC(12,2)`; subtotales y totales usan `NUMERIC(14,2)` para soportar cantidades y acumulación de líneas. Precio y costo de productos admiten de cero a 9999999999.99 y hasta dos decimales; DTOs, servicios y entidades rechazan el exceso sin redondear, y normalizan valores válidos a escala dos. Las ventas almacenan snapshots para que futuros cambios de producto no reescriban el historial comercial.

## Inventario y concurrencia

`ProductService` bloquea también las modificaciones de catálogo y los cambios de estado mediante `PESSIMISTIC_WRITE`, para que una edición concurrente no sobrescriba stock con valores anteriores. `ProductRepository.findByIdForUpdate` usa una implementación JPA que hace flush de los cambios pendientes de la transacción, adquiere `PESSIMISTIC_WRITE` y refresca el producto bajo ese bloqueo. El bloqueo por sí solo no actualiza una entidad ya cargada en la sesión; el refresco evita validar estado o stock obsoleto, y el flush previo conserva cambios propios de operaciones anteriores en la misma transacción. `InventoryService` ejecuta entradas y salidas dentro de transacciones. Obtiene el producto con `PESSIMISTIC_WRITE`, valida límites/suficiencia, actualiza el stock mediante métodos de dominio y guarda `StockMovement` en la misma transacción. `StockMovement` es `@Immutable`; el historial se pagina por `created_at DESC, id DESC`.

## Autenticación

Con autenticación habilitada (valor por defecto), el primer arranque crea una única cuenta desde `APP_ADMIN_EMAIL` y `APP_ADMIN_PASSWORD`. La contraseña se guarda con BCrypt. `POST /api/auth/login` entrega un JWT HS256 de ocho horas firmado con `APP_JWT_SECRET`; sólo login y `GET /actuator/health` son públicos, y el resto de la API exige un bearer token. No hay registro público, recuperación de contraseña ni múltiples roles. En instalaciones privadas, el cliente trata centralmente los 401 de solicitudes protegidas: vuelve al login y, tras ingresar, restaura la sección anterior. Conserva la recuperación idempotente en sessionStorage y no reintenta escrituras automáticamente. El token y una versión de sesión capturados al enviar impiden que un 401 tardío cierre una sesión nueva; el 401 del login se mantiene como error de credenciales.

## Pruebas

Hay pruebas unitarias de entidades, servicios, validación, mappers y controllers; pruebas JPA con `@DataJpaTest` y PostgreSQL 17/Testcontainers; pruebas de integración de servicios; y E2E HTTP con `@SpringBootTest` y MockMvc. El agregado y repositorio de ventas cuentan con pruebas unitarias y JPA.

## Decisiones y límites actuales

`SaleService` bloquea los productos de solicitudes HTTP en orden ascendente de ID antes de leer stock y construir snapshots, evitando usar entidades obsoletas de la sesión JPA. Ambas formas de confirmación verifican el estado activo actual bajo esos bloqueos antes de cualquier descuento; un producto inactivo devuelve 409 `PRODUCT_INACTIVE`. Desactivar impide nuevas ventas, pero no ajustes de inventario ni recuperación idempotente de ventas confirmadas. La interfaz trata el rechazo como definitivo, limpia únicamente su recuperación y conserva el carrito editable. Confirma agregados con ítems, verifica que `sales.total` coincida con la suma de subtotales y ordena las líneas por `productId` antes de descontar cada una mediante `InventoryService` en la misma transacción. Así los bloqueos pesimistas de productos se adquieren en orden determinista y se evitan deadlocks entre ventas con líneas invertidas; las ventas concurrentes no pueden sobrepasar el stock disponible. Cada descuento registra un movimiento `OUT` con motivo `Sale`; una falta de stock revierte venta, stock y movimientos. `GlobalExceptionHandler` expone `EMPTY_SALE` (400), `PRODUCT_NOT_FOUND` (404) e `INSUFFICIENT_STOCK` (409) sin detalles internos. `POST /api/sales` valida el contrato, construye la venta desde IDs de producto y devuelve `201 Created` con su detalle histórico. La igualdad entre `sales.total` y la suma de ítems se garantiza en el servicio transaccional, no mediante un CHECK entre tablas. GET `/api/sales` pagina resúmenes por fecha e ID descendentes (máximo 100), sin cargar ítems. GET `/api/sales/{id}` carga explícitamente los ítems dentro de una transacción read-only y devuelve snapshots; una venta inexistente responde 404 `SALE_NOT_FOUND`. El historial de la interfaz usa ARS y horario argentino, conserva la página al volver del detalle y cancela consultas obsoletas.

## Selección paginada de productos

GET `/api/products/lookup?q=&sellable=false&page=0&size=20` busca subcadenas literales de nombre o SKU sin distinguir mayúsculas, con máximo 100 y orden nombre/ID. `locate` permite tratar `%` y `_` como texto sin convertirlos en comodines. PostgreSQL filtra y cuenta antes de paginar. Ventas usa `sellable=true` (activo y stock positivo); inventario incluye todos. El contrato `/search?name=` permanece intacto. El selector compartido cancela respuestas obsoletas y mantiene la selección y el carrito fuera de los resultados visibles. Los errores de refresco posteriores a una operación confirmada lo aclaran explícitamente.

## Métricas transaccionales y confiabilidad

Los servicios de ventas e inventario registran counters de negocio mediante BusinessMetrics: éxitos sólo en afterCommit del límite transaccional exterior; rechazos de venta acotados sólo al completarse rollback. La recuperación idempotente lee historial sin emitir otra venta/movimiento. No se persisten métricas ni se modifica esquema. Son telemetría best effort, no contabilidad. El perfil observability muestrea readiness nativa fuera del scrape. Compose añade Alertmanager sin receptor externo; recording/alert rules y SRE Overview están versionados. [SRE](SRE.md) concentra definiciones y límites.

## Kubernetes local

El laboratorio Etapa 5 usa namespace stockflow en kind: dos réplicas backend detrás de ClusterIP, frontend Vite con proxy DNS interno, PostgreSQL 17 StatefulSet/PVC y observabilidad con Deployments/PVCs. Flyway y bootstrap mantienen coordinación PostgreSQL en cada arranque. Management 9091 no forma parte del Service backend ni se publica al host; Prometheus descubre pods mediante RBAC namespaced. No hay Ingress, Helm ni operadores. [KUBERNETES](KUBERNETES.md) documenta seguridad, recursos y límites de nodo único. PostgreSQL dentro de Kubernetes se utiliza aquí para laboratorio; en producción/cloud evaluaremos una base administrada.
