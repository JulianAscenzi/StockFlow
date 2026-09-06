# Status

## Completed modules

`category`, `product`, `inventory`, `common` y `sale` hasta su confirmación persistente están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

`sale`: la confirmación valida ítems y total, bloquea productos en orden determinista, descuenta stock y registra movimientos `OUT` de forma atómica. Sus contratos y errores HTTP públicos están definidos; falta el controller.

## Next task

Implementar el controller de ventas con confirmación y consultas necesarias.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **299 pruebas, 0 fallos, 0 errores y 0 omitidas**.

## Pending decisions

Las métricas del dashboard, el alcance de autenticación y el frontend se definirán después del backend MVP. No bloquean la próxima tarea.

## Real blockers

No hay bloqueos reales conocidos.
