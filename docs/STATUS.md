# Status

## Completed modules

El backend MVP está cerrado: `category`, `product`, `inventory`, `common`, `sale` y `dashboard` están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

`frontend`: cliente React + TypeScript + Vite integrado con la API mediante el proxy de desarrollo. El catálogo usa el listado general hasta que se ingresa una búsqueda, y los formularios conservan su referencia durante la operación asíncrona para reflejar correctamente altas y movimientos.

## Next task

Documentar ejecución y despliegue local reproducible.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **309 pruebas, 0 fallos, 0 errores y 0 omitidas**. La integración frontend–backend se verificó además en una base PostgreSQL temporal: categoría, producto, entrada de stock, venta y resumen diario.

## Pending decisions

Queda definir el alcance futuro de autenticación.

## Real blockers

No hay bloqueos reales conocidos. El proxy de Vite apunta a `localhost:8080` por defecto y puede redirigirse con `VITE_API_PROXY_TARGET` para pruebas aisladas.
