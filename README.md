# StockFlow

StockFlow es una aplicación web de inventario y ventas para pequeños comercios. Permite administrar categorías y productos, registrar entradas o salidas de stock y confirmar ventas. El resumen muestra las métricas del día en la zona horaria de Argentina.

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

`.env` define `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_PORT`; consultá `.env.example` para sus valores de desarrollo. Cambiá la contraseña antes de usar una base fuera de tu equipo y nunca publiques `.env`.

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

El despliegue de producción aún no está preparado: falta definir la configuración segura, el hosting y el checklist operativo del bloque 22 del roadmap. No expongas esta instancia de desarrollo a Internet ni la uses con datos de producción.
