# Ejecución local y desarrollo

Guía operativa de Compose, Maven/Vite y pruebas. Para entender el producto y las decisiones principales, comenzar por el [README](../README.md). No requiere una cuenta cloud. Todos los comandos parten de la raíz salvo indicación explícita.

## Requisitos

Para el entorno completo: Docker con Docker Compose y conexión a Internet en el primer build.
Para ejecutar procesos fuera de Docker: Java 21 y Node 24+ con npm.

## Arranque local con Docker Compose

Desde la raíz, prepará una sola vez la configuración:

```bash
cp .env.example .env
```

Antes de arrancar, reemplazá `POSTGRES_PASSWORD`, `APP_ADMIN_PASSWORD`, `APP_JWT_SECRET` y `GRAFANA_ADMIN_PASSWORD` por valores propios. El JWT de ejemplo tiene menos de los 32 caracteres requeridos y Grafana no tiene contraseña de ejemplo: copiar el archivo solo no basta. Generá cada valor por separado con `openssl rand -hex 32` y pegalo en tu `.env` local; los valores hexadecimales también permiten cargarlo desde Bash sin problemas de quoting. `.env` está ignorado y no se copia a las imágenes. Si ya tenés un `.env`, agregá las variables faltantes sin sobrescribirlo.

Levantá toda la aplicación:

```bash
docker compose up --build
```

Abrí `http://localhost:5173` e ingresá con `APP_ADMIN_EMAIL` y `APP_ADMIN_PASSWORD`. PostgreSQL debe estar healthy antes de iniciar la API, y la API antes de iniciar Vite. Flyway aplica o valida V1–V5; Hibernate valida el esquema. Para segundo plano: `docker compose up --build -d --wait`.

```text
Navegador → localhost:5173 → frontend (Vite)
                              /api → backend:8080 → database:5432
```

Compose publica `127.0.0.1:5173` (interfaz), `127.0.0.1:5432` (PostgreSQL, para conservar el desarrollo con Maven), `127.0.0.1:9090` (Prometheus) y `127.0.0.1:3000` (Grafana) y `127.0.0.1:9093` (Alertmanager). La API permanece en la red interna. El navegador usa rutas relativas `/api`; Vite resuelve `backend` dentro de Docker. No hace falta CORS entre la interfaz y su proxy. El healthcheck del backend consulta su listener de management en la red interna; los demás usan loopback dentro de cada contenedor.

El frontend es un contenedor local con Vite, dependencias instaladas mediante `npm ci` y código copiado en el build. Después de editar fuentes, repetí `docker compose up --build`; no hay bind mounts ni hot reload desde el host. No es una imagen de producción: en Vercel se siguen sirviendo archivos estáticos de `npm run build`.

Verificación operativa:

```bash
docker compose config --quiet
docker compose ps
docker compose exec backend curl --fail http://backend-management:9091/actuator/health
docker compose logs backend
```

`docker compose config` sin `--quiet` muestra valores resueltos, incluidos secretos; no publiques su salida. Cambiar `POSTGRES_PASSWORD` en `.env` no cambia la contraseña de una base ya inicializada. Tampoco cambiar `APP_ADMIN_PASSWORD` modifica un administrador existente: esas credenciales se usan en el primer arranque.

## Desarrollo con Maven y Vite fuera de Docker

Primero detené el entorno completo si está iniciado (`docker compose down`, sin `-v`). Con el mismo `.env`:

1. Iniciá PostgreSQL y esperá a que el servicio quede saludable.

   ```bash
   docker compose up -d database
   docker compose ps
   ```

2. En una terminal, cargá las variables de `.env` e iniciá el backend. Flyway aplicará las migraciones automáticamente; Hibernate sólo valida el esquema.

   ```bash
   set -a; . ./.env; set +a
   cd backend
   ./mvnw spring-boot:run
   ```

   El backend queda disponible en `http://localhost:8080`.

3. En otra terminal, instalá las dependencias e iniciá la interfaz.

   ```bash
   cd frontend
   npm ci
   VITE_AUTH_ENABLED=true npm run dev
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

## Configuración

Las probes, logs, request ID, timeouts y el comportamiento ante una caída de PostgreSQL se documentan en [Operación del backend](OPERATIONS.md).

| Variable | Uso local |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base y credenciales, obligatorias |
| `POSTGRES_PORT` | Puerto PostgreSQL del host; por defecto 5432 |
| `FRONTEND_PORT` | Puerto de interfaz en Compose; por defecto 5173 |
| `APP_AUTH_ENABLED` | Autenticación y login en Compose; por defecto true |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | Administrador inicial |
| `APP_JWT_SECRET` | Firma JWT, al menos 32 caracteres |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | Administrador Grafana; contraseña obligatoria |
| `PROMETHEUS_PORT`, `GRAFANA_PORT`, `ALERTMANAGER_PORT` | UIs locales: 9090, 3000, 9093 por defecto |

Compose fija `POSTGRES_HOST=database`, el puerto interno de PostgreSQL en 5432 y `PORT=8080`; el puerto publicado del host no altera esos valores. En Maven el host sigue siendo localhost por defecto. No se carga todo `.env` en los contenedores: sólo las variables declaradas.

Compose fija `VITE_API_BASE_URL` vacío y `VITE_API_PROXY_TARGET=http://backend:8080`; deriva `VITE_AUTH_ENABLED` de `APP_AUTH_ENABLED`. Ninguna variable `VITE_*` contiene secretos. La ejecución manual de Vite con autenticación requiere `VITE_AUTH_ENABLED=true npm run dev`. Render conserva `DATABASE_URL` y Vercel conserva `VITE_API_BASE_URL`/`VITE_AUTH_ENABLED`; sus configuraciones no cambian. La primera ejecución crea un único administrador con email y contraseña hasheada con BCrypt. Cambiá las contraseñas y el secreto antes de usar una base fuera de tu equipo y nunca publiques `.env`.

El proxy de Vite usa `http://localhost:8080` por defecto. Para apuntar temporalmente a otra instancia local, por ejemplo en una prueba aislada, ejecutá:

```bash
cd frontend
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev
```

## Detener el entorno

Para detener los servicios sin borrar los datos locales:

```bash
docker compose down
```

No ejecutes `docker compose down -v` salvo que quieras eliminar deliberadamente el volumen de PostgreSQL y todos sus datos.

## Pruebas de navegador aisladas

Requieren Java 21, Node 24+, npm y Docker accesible. Desde la raíz:

```bash
npm --prefix frontend ci
npm --prefix frontend exec -- playwright install --with-deps chromium
npm --prefix frontend run test:e2e
```

El comando público invoca Maven y `BrowserE2EIT`, que crea PostgreSQL 17 y Spring Boot en un puerto aleatorio con credenciales exclusivas de pruebas. Playwright inicia Vite en otro puerto libre, con proxy a ese backend. No se reutilizan la demo ni servicios locales. El script interno `test:e2e:browser` requiere el entorno provisto por Java. Chromium usa un worker y cero reintentos; una suite vacía, omitida, fallida o sin informe falla la ejecución. Los diagnósticos quedan en `backend/target/browser-e2e.log` y `frontend/test-results/` (trazas y capturas de los fallos).

Dependencias de jobs CI, estrategia de tags/digest, SBOM, retención de artifacts y límites: [CONTAINER_REGISTRY](CONTAINER_REGISTRY.md). Los informes de tests se conservan siete días. Referencias de configuración: [servidor de Playwright](https://playwright.dev/docs/test-webserver), [setup-java](https://github.com/actions/setup-java), [setup-node](https://github.com/actions/setup-node) y [artefactos](https://github.com/actions/upload-artifact).

## Verificación backend

```bash
cd backend
./mvnw test
```

Requiere Java 21 y Docker accesible para PostgreSQL Testcontainers. La suite habitual tiene 385 tests; `BrowserE2EIT` se invoca explícitamente con el runner de navegador y no forma parte de ese total. Los reportes locales pueden mezclar ambas ejecuciones: no sumar XML antiguos para inferir una suite nueva.

Para compilar la interfaz después de `npm ci`: `npm --prefix frontend run build`. Las probes y comportamiento de recuperación se explican en [OPERATIONS](OPERATIONS.md); accesos Grafana/Prometheus/Alertmanager en [OBSERVABILITY](OBSERVABILITY.md) y [SRE](SRE.md).
