# Capturas y diagramas para portfolio

Esta carpeta contiene imágenes reales del proyecto. No se agregan renders de dashboards, CI o AWS que simulen ejecuciones. El [README](../../README.md) usa Mermaid para arquitectura; [ARCHITECTURE](../ARCHITECTURE.md#arquitectura-aws-diseñada--sin-provisionar) contiene el diseño AWS. Son diagramas explicativos, no capturas de infraestructura desplegada.

## Inventario actual

| Recurso | Estado y alcance |
| --- | --- |
| [product-overview.png](product-overview.png) | Resumen actualizado, 2026-11-13, código funcional `3699a59`, Chromium 1440×960. Instancia local aislada, datos ficticios de librería. |
| [sales-history.png](sales-history.png) | Historial de tres ventas en la misma instancia y fecha. |
| [sale-detail.png](sale-detail.png) | Detalle de venta #2 con snapshots, cantidades y total en la misma instancia. |
| [dashboard-demo.png](dashboard-demo.png) | Captura real del resumen, anterior al historial de ventas. Sirve para presentar producto, no como evidencia del estado actual ni de Grafana. |
| Arquitectura general | Mermaid versionado en README, disponible sin crear una imagen adicional. |
| Arquitectura AWS | Mermaid en ARCHITECTURE; rotulado como diseño sin provisionar. |
| Grafana, Actions y Kubernetes | Sin capturas versionadas. STATUS registra verificaciones y rutas temporales de evidencia; no garantiza que esas imágenes sigan disponibles. |

## Contexto de las capturas actuales

Las tres imágenes nuevas se tomaron contra el backend real y PostgreSQL 17/Testcontainers en una ejecución aislada posterior a las suites de `3699a59`. Se cargaron por HTTP una categoría, cuatro productos, cuatro entradas y tres ventas con datos ficticios; no se agregaron fixtures permanentes. El resumen muestra tres ventas, ARS 17.200 de facturación, diez unidades, ARS 8.200 de margen bruto y dos productos con bajo stock. El total de la venta #2 es ARS 8.200.

La captura se automatizó mediante un escenario Playwright temporal y un filtro temporal del runner BrowserE2EIT; ambos cambios de instrumentación se retiraron después. El escenario aprobó y no registró errores JavaScript de página. Las imágenes se inspeccionaron visualmente antes de incorporarlas; no contienen contraseñas, tokens, cookies ni datos personales. La instancia de captura no es la demo pública ni una infraestructura cloud y estas pantallas no acreditan disponibilidad actual de ningún servicio.

## Checklist manual

Capturar en un laboratorio disponible, con datos ficticios. Incluir fecha/contexto y el commit o digest observado en un pie de imagen; no sugerir que una captura histórica representa servicios actuales. No es necesario levantar AWS.

- [x] **Producto actualizado** — `product-overview.png`, `sales-history.png` y `sale-detail.png`: capturas reales del resumen, historial y detalle, con contexto registrado arriba.
- [ ] **Grafana Application Overview** — `grafana-application-overview.png`: dashboard provisionado, tráfico real y paneles HTTP/JVM/Hikari. [Acceso](../OBSERVABILITY.md#arranque-y-accesos).
- [ ] **Grafana SRE Overview** — `grafana-sre-overview.png`: cobertura, disponibilidad, budget, latencia y negocio. Mostrar “sin tráfico”/historia parcial si corresponde; no simular 30 días. [Definiciones](../SRE.md).
- [ ] **GitHub Actions exitoso** — `github-actions.png`: run real, SHA y jobs de tests/OCI/smoke/publicación. Usar [run registrado](https://github.com/JulianAscenzi/StockFlow/actions/runs/37237744986) o uno nuevo verificado; indicar cuál.
- [ ] **Kubernetes Ready** — `kubernetes-ready.png`: `kubectl --context kind-stockflow-lab -n stockflow get pods` en el laboratorio correcto; opcionalmente PVCs y targets UP. No mostrar Secrets, kubeconfig o entorno.
- [ ] **Arquitectura general exportada (opcional)** — `architecture-overview.png`: exportar el Mermaid real del README para usar en CV/LinkedIn; no representa una captura runtime.
- [ ] **Diseño Terraform/AWS exportado (opcional)** — `aws-design.png`: exportar el diagrama de ARCHITECTURE con el rótulo “Diseñado / sin provisionar”. No mostrar una consola AWS ficticia.

Antes de guardar: revisar que no aparezcan contraseñas, JWT, cookies, headers, tokens, emails personales, kubeconfig ni valores de `.env`/state/plan. Preferir pantallas sin información sensible. No versionar los artifacts runtime completos para conseguir una captura.

Actualizar esta tabla y enlazar una selección breve desde README cuando existan las nuevas capturas. Las pendientes son material visual adicional, no evidencia inventada ni bloqueos del alcance técnico.
