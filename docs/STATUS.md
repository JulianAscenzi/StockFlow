# Status

## Completed modules

El backend MVP está cerrado: `category`, `product`, `inventory`, `common`, `sale` y `dashboard` están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

`deployment`: la demo gratuita de portfolio está publicada en [Vercel](https://frontend-beta-plum-15.vercel.app/) y la API en [Render](https://stockflow-api-0fan.onrender.com/actuator/health). Usa Docker para la API, PostgreSQL 17 Free, CORS por origen explícito y health check. No contiene secretos ni debe usar datos reales.

## Next task

Definir el alcance de autenticación y la infraestructura persistente antes de habilitar uso comercial. La demo pública actual sólo sirve como muestra de portfolio.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **312 pruebas, 0 fallos, 0 errores y 0 omitidas**. La interfaz compila con Vite y la integración frontend–backend se verificó además en una base PostgreSQL temporal: categoría, producto, entrada de stock, venta y resumen diario.

## Pending decisions

Queda definir e implementar el alcance de autenticación propio antes de usar datos comerciales. La demo de portfolio no debe contener datos reales.

## Real blockers

No hay bloqueos técnicos para la demo pública. El avance hacia uso comercial requiere una decisión de producto sobre autenticación y una infraestructura con persistencia y respaldos.
