# Status

## Completed modules

`category`, `product`, `inventory`, `common` y `sale` hasta su confirmación persistente están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

`sale`: la confirmación HTTP valida contratos, crea la venta desde productos, bloquea en orden determinista, descuenta stock y registra movimientos `OUT` de forma atómica.

## Next task

Verificar el flujo E2E de ventas por HTTP con PostgreSQL/Testcontainers.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **302 pruebas, 0 fallos, 0 errores y 0 omitidas**.

## Pending decisions

Las métricas del dashboard, el alcance de autenticación y el frontend se definirán después del backend MVP. No bloquean la próxima tarea.

## Real blockers

No hay bloqueos reales conocidos.
