# StockFlow

StockFlow es una aplicación web de inventario y ventas para pequeños comercios. Permite administrar categorías y productos, registrar entradas o salidas de stock y confirmar ventas. El resumen muestra las métricas del día en la zona horaria de Argentina.

## Demo pública

- [Abrir la aplicación](https://frontend-beta-plum-15.vercel.app/)
- [Estado de la API](https://stockflow-api-0fan.onrender.com/actuator/health)

La demo usa datos ficticios y está publicada con servicios gratuitos. La API puede tardar aproximadamente un minuto en responder después de un período de inactividad.

![Resumen diario de StockFlow](docs/images/dashboard-demo.png)

## Decisiones técnicas destacadas

- PostgreSQL y Flyway son la fuente de verdad del esquema; Hibernate valida, no genera tablas en ejecución.
- Las ventas conservan snapshots de nombre, SKU, precio y costo, para que el historial no cambie al editar el catálogo.
- Las salidas de inventario usan bloqueo pesimista y cada variación registra su movimiento histórico en la misma transacción.
- Las líneas de una venta se bloquean por ID en orden determinista para reducir conflictos entre ventas concurrentes.
- La suite de integración usa PostgreSQL real mediante Testcontainers; no depende de una base H2 distinta del entorno operativo.

## Recorrido de evaluación (1 minuto)

1. Abrí la demo y entrá a **Productos**. Creá una categoría y un producto con precio, costo y stock mínimo.
2. En **Inventario**, registrá una entrada para ese producto.
3. En **Nueva venta**, agregalo, elegí una cantidad y confirmá. Mientras la operación está en curso, los controles quedan bloqueados para evitar un doble envío.
4. Volvé a **Resumen**: se reflejan la venta, facturación, unidades, margen bruto estimado y, si corresponde, el aviso de stock bajo.

El catálogo de productos se navega por páginas y los selectores de categorías y productos activos cargan todas sus páginas, por lo que el flujo sigue disponible aunque haya más de 100 registros.

## Requisitos

- Java 21
- Node.js con npm
- Docker y Docker Compose

## Arranque local

1. Creá la configuración local a partir del ejemplo. No subas el archivo resultante al repositorio.

   ```bash
   cp .env.example .env
   ```

2. Iniciá PostgreSQL y esperá a que el servicio quede saludable.

   ```bash
   docker compose up -d database
   docker compose ps
   ```

3. En una terminal, cargá las variables de `.env` e iniciá el backend. Flyway aplicará las migraciones automáticamente; Hibernate sólo valida el esquema.

   ```bash
   set -a; . ./.env; set +a
   cd backend
   ./mvnw spring-boot:run
   ```

   El backend queda disponible en `http://localhost:8080`.

4. En otra terminal, instalá las dependencias e iniciá la interfaz.

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

   Abrí `http://localhost:5173`. Durante el desarrollo, Vite redirige las solicitudes `/api` al backend local.

## Primer uso

1. En **Productos**, creá una categoría.
2. Creá un producto y definí su precio, costo y stock mínimo.
3. En **Inventario**, registrá una entrada de stock.
4. En **Nueva venta**, agregá el producto y confirmá la venta.
5. Volvé al **Resumen** para consultar ventas, facturación, margen bruto estimado y productos a reponer.

Las ventas y movimientos de stock quedan registrados como historial. Una venta descuenta el stock en la misma operación.

## API en breve

Todas las rutas devuelven DTOs JSON; las rutas de catálogo paginadas incluyen `content`, `page`, `size`, `totalElements` y `totalPages`.

| Operación | Ruta | Ejemplo mínimo |
| --- | --- | --- |
| Consultar resumen diario | `GET /api/dashboard` | `GET /api/dashboard?size=8` |
| Listar productos | `GET /api/products` | `GET /api/products?page=0&size=20` |
| Registrar entrada | `POST /api/products/{id}/stock/in` | `{"quantity": 10, "reason": "Recepción"}` |
| Confirmar venta | `POST /api/sales` | `{"items":[{"productId":1,"quantity":2}]}` |

Con autenticación habilitada, las rutas de negocio requieren `Authorization: Bearer <token>` y el token se obtiene con `POST /api/auth/login`. La demo pública la desactiva intencionalmente.

## Verificación

Para compilar la interfaz:

```bash
cd frontend
npm run build
```

Para ejecutar la suite del backend con PostgreSQL real mediante Testcontainers:

```bash
cd backend
./mvnw test
```

Docker debe estar disponible para las pruebas de integración. Para una comprobación manual, confirmá que `GET http://localhost:8080/api/dashboard` responde JSON y que la interfaz carga su resumen sin avisos de error.

## Configuración

`.env` define `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` y `APP_JWT_SECRET`; consultá `.env.example` para valores locales. La primera ejecución crea un único administrador con email y contraseña hasheada con BCrypt. Cambiá las contraseñas y el secreto antes de usar una base fuera de tu equipo y nunca publiques `.env`.

El proxy de Vite usa `http://localhost:8080` por defecto. Para apuntar temporalmente a otra instancia local, por ejemplo en una prueba aislada, ejecutá:

```bash
cd frontend
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev
```

## Detener el entorno

Para detener los servicios sin borrar los datos locales:

```bash
docker compose stop
```

No ejecutes `docker compose down -v` salvo que quieras eliminar deliberadamente el volumen de PostgreSQL y todos sus datos.

## Despliegue

Para una demo gratuita de portfolio se usa Vercel (interfaz) y Render Free (API y PostgreSQL). Consultá la [guía de despliegue](docs/DEPLOYMENT.md). No cargues datos reales: Render Free suspende la API inactiva y elimina la base después de 30 días. La demo pública deja la autenticación desactivada intencionalmente; para un uso comercial debe habilitarse con secretos propios.

### Pruebas de navegador aisladas

Requieren Java 21, Node 24+, npm y Docker accesible. Desde la raíz:

```bash
npm --prefix frontend ci
npm --prefix frontend exec -- playwright install chromium
npm --prefix frontend run test:e2e
```

El comando público invoca Maven y `BrowserE2EIT`, que crea PostgreSQL 17 y Spring Boot en un puerto aleatorio con credenciales exclusivas de pruebas. Playwright inicia Vite en otro puerto libre, con proxy a ese backend. No se reutilizan la demo ni servicios locales. El script interno `test:e2e:browser` requiere el entorno provisto por Java. Chromium usa un worker y cero reintentos; una suite vacía, omitida, fallida o sin informe falla la ejecución. Los diagnósticos quedan en `backend/target/browser-e2e.log` y `frontend/test-results/` (trazas y capturas de los fallos).
