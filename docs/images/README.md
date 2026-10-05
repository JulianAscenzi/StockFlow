# Capturas y diagramas para portfolio

Esta carpeta contiene imágenes reales del proyecto. No se agregan renders de dashboards, CI o AWS que simulen ejecuciones. El [README](../../README.md) usa Mermaid para arquitectura; [ARCHITECTURE](../ARCHITECTURE.md#arquitectura-aws-diseñada--sin-provisionar) contiene el diseño AWS. Son diagramas explicativos, no capturas de infraestructura desplegada.

## Inventario actual

| Recurso | Estado y alcance |
| --- | --- |
| [dashboard-demo.png](dashboard-demo.png) | Captura real del resumen, anterior al historial de ventas. Sirve para presentar producto, no como evidencia del estado actual ni de Grafana. |
| Arquitectura general | Mermaid versionado en README, disponible sin crear una imagen adicional. |
| Arquitectura AWS | Mermaid en ARCHITECTURE; rotulado como diseño sin provisionar. |
| Grafana, Actions y Kubernetes | Sin capturas versionadas. STATUS registra verificaciones y rutas temporales de evidencia; no garantiza que esas imágenes sigan disponibles. |

## Checklist manual

Capturar en un laboratorio disponible, con datos ficticios. Incluir fecha/contexto y el commit o digest observado en un pie de imagen; no sugerir que una captura histórica representa servicios actuales. No es necesario levantar AWS.

- [ ] **Producto actualizado** — `product-overview.png`: resumen e historial accesibles en la UI actual.
- [ ] **Grafana Application Overview** — `grafana-application-overview.png`: dashboard provisionado, tráfico real y paneles HTTP/JVM/Hikari. [Acceso](../OBSERVABILITY.md#arranque-y-accesos).
- [ ] **Grafana SRE Overview** — `grafana-sre-overview.png`: cobertura, disponibilidad, budget, latencia y negocio. Mostrar “sin tráfico”/historia parcial si corresponde; no simular 30 días. [Definiciones](../SRE.md).
- [ ] **GitHub Actions exitoso** — `github-actions.png`: run real, SHA y jobs de tests/OCI/smoke/publicación. Usar [run registrado](https://github.com/JulianAscenzi/StockFlow/actions/runs/37237744986) o uno nuevo verificado; indicar cuál.
- [ ] **Kubernetes Ready** — `kubernetes-ready.png`: `kubectl --context kind-stockflow-lab -n stockflow get pods` en el laboratorio correcto; opcionalmente PVCs y targets UP. No mostrar Secrets, kubeconfig o entorno.
- [ ] **Arquitectura general exportada (opcional)** — `architecture-overview.png`: exportar el Mermaid real del README para usar en CV/LinkedIn; no representa una captura runtime.
- [ ] **Diseño Terraform/AWS exportado (opcional)** — `aws-design.png`: exportar el diagrama de ARCHITECTURE con el rótulo “Diseñado / sin provisionar”. No mostrar una consola AWS ficticia.

Antes de guardar: revisar que no aparezcan contraseñas, JWT, cookies, headers, tokens, emails personales, kubeconfig ni valores de `.env`/state/plan. Preferir pantallas sin información sensible. No versionar los artifacts runtime completos para conseguir una captura.

Actualizar esta tabla y enlazar una selección breve desde README cuando existan las nuevas capturas. Las pendientes son material visual adicional, no evidencia inventada ni bloqueos del alcance técnico.
