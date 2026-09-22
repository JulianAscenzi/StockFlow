# Guion de demostración

Objetivo: mostrar en unos cinco minutos cómo StockFlow conecta catálogo, inventario, ventas y resumen diario, y explicar las garantías del backend.

## Preparación

Abrí la aplicación desde los enlaces del [README](../../README.md). Revisá los [límites de despliegue](../DEPLOYMENT.md) y comprobá que la demo responda antes de presentarla. Usá datos ficticios y un sufijo único para la categoría y el SKU, por ejemplo fecha y hora, para evitar colisiones con demostraciones anteriores.

Anotá las métricas iniciales del resumen: la demo puede contener operaciones de otras personas. Los incrementos de abajo suponen que nadie más registra ventas durante el recorrido y que no cambia el día en Argentina.

## Recorrido

1. **Presentación:** “StockFlow permite administrar productos, registrar movimientos de inventario y confirmar ventas con un resumen diario”.
2. **Productos:** creá una categoría ficticia y un producto “Cuaderno demo”, con SKU único, precio `1500`, costo `900` y stock mínimo `3`.
3. **Inventario:** registrá una entrada de `5` unidades con motivo “Demostración de portfolio”. Mostrá el stock resultante y su movimiento.
4. **Nueva venta:** agregá `2` unidades de ese producto y confirmá. El total esperado es `3000` y quedan `3` unidades.
5. **Resumen:** verificá los incrementos esperados de una venta, `3000` de facturación, dos unidades y `1200` de margen bruto estimado. El producto cumple la condición de bajo stock porque su stock es igual al mínimo; buscalo en el listado paginado si no aparece en la primera página.
6. **Persistencia:** recargá y comprobá que el stock y la venta reflejada en las métricas se conservan.

El margen bruto estimado surge de `(1500 - 900) × 2`; no representa ganancia neta ni cobros. El procedimiento es un guion para ejecutar, no un registro de una verificación ya realizada.

## Explicación técnica breve

- **Consistencia:** venta, descuento de stock y movimientos confirman o revierten juntos.
- **Concurrencia:** bloqueo pesimista y orden por ID de producto. Usá la nota [Bloqueo de stock](../decisiones/Bloqueo%20de%20stock.md) para mostrar evidencia y límites.
- **Historial:** las ventas conservan snapshots de nombre, SKU, precio y costo.
- **Persistencia:** Flyway administra el esquema y Hibernate lo valida.
- **Verificación:** las pruebas de integración usan PostgreSQL con Testcontainers. Consultá el resultado documentado en [Estado](../STATUS.md) antes de citar cantidades.

## Alcance que conviene explicar

La demo de portfolio es pública y utiliza datos ficticios. La aplicación dispone de autenticación de administrador para instalaciones privadas; la demo la desactiva intencionalmente. Una operación comercial requiere resolver persistencia, backups, secretos y operación según la guía de despliegue.

## Notas después de presentar

- Fecha y contexto:
- Preguntas recibidas:
- Dificultades observadas:
- Mejoras propuestas, todavía sin incorporar al roadmap:

[Volver al inicio](../Inicio.md) · [Arquitectura](../ARCHITECTURE.md)
