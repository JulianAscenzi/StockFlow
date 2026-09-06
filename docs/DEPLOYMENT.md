# Demo gratuita para portfolio

Esta guía publica una demo técnica gratuita: Vercel para la interfaz y Render Free para API y PostgreSQL. No es un despliegue de producción ni debe contener datos reales, porque la aplicación todavía no tiene autenticación propia.

## Límites intencionales

- Render Free detiene la API tras 15 minutos sin tráfico; la primera visita posterior puede demorar aproximadamente un minuto.
- PostgreSQL Free en Render tiene 1 GB, no tiene backups y expira 30 días después de crearse. Se puede recrear con datos ficticios cuando haga falta.
- No se compra dominio ni se configura Cloudflare. Se usan los subdominios gratuitos de Vercel y Render.
- La API restringe CORS al dominio de Vercel, pero eso no es autenticación. Cualquiera podría modificar la demo haciendo llamadas directas a la API: usá sólo información inventada.

## 1. API y base en Render

1. En Render, abrí **New** → **Blueprint** y elegí este repositorio cuando el cambio a `plan: free` ya esté visible en GitHub.
2. Confirmá que el plan de `stockflow-api` y `stockflow-postgres` sea **Free**; no aceptes una opción paga.
3. Render pedirá `APP_CORS_ALLOWED_ORIGINS`. Ingresá temporalmente `https://example.com`; se reemplaza después de crear la interfaz.
4. Aplicá el Blueprint. Render crea la base PostgreSQL 17, inyecta internamente `DATABASE_URL` y despliega la API.
5. Guardá la URL `https://stockflow-api-<identificador>.onrender.com` y comprobá `<esa-url>/actuator/health`: debe responder `200`.

## 2. Interfaz en Vercel

1. En Vercel, hacé **Add New** → **Project** e importá el mismo repositorio.
2. Configurá **Root Directory** como `frontend`. Vercel detectará Vite.
3. Antes de desplegar, agregá la variable de producción `VITE_API_BASE_URL` con la URL de Render, sin barra final.
4. Desplegá y guardá la URL `https://<proyecto>.vercel.app`.
5. Volvé a Render, editá `APP_CORS_ALLOWED_ORIGINS` y reemplazá el valor temporal por esa URL de Vercel. Guardá y redeployá la API.

`VITE_API_BASE_URL` es pública y puede estar en la configuración de Vercel; nunca cargues contraseñas en una variable que empiece con `VITE_`.

## Smoke test de la demo

1. Abrí la URL de Vercel y esperá el arranque de Render si corresponde.
2. Creá una categoría y un producto ficticios.
3. Registrá una entrada de stock, confirmá una venta y verificá el resumen diario.
4. Recargá la página y verificá que los datos de demostración persistan.
5. Incluí ambas URLs y una captura de pantalla en tu CV o portfolio.

Si la base vence, recreá el Blueprint y repetí el smoke test con nuevos datos ficticios. Para una operación comercial futura, se necesita un plan con persistencia/respaldos y autenticación de aplicación.
