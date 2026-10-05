# StockFlow — Inicio

Este es el punto de entrada al cuaderno técnico de StockFlow: documentación del producto, decisiones respaldadas por el código y material para presentar el proyecto.

## Abrir en Obsidian

1. En el selector de bóvedas, elegí **Open folder as vault** y seleccioná la carpeta `docs` de este repositorio.
2. Abrí esta nota, `Inicio.md`.
3. En **Settings → Files and Links**, desactivá **Use Wikilinks** y elegí rutas relativas al archivo para los enlaces nuevos. Así las notas también se pueden recorrer desde GitHub.

Las notas se versionan con Git. La carpeta `docs/.obsidian/`, que Obsidian crea con tus preferencias personales, está ignorada. No hacen falta plugins adicionales. Los enlaces al código salen de la bóveda: consultalos desde GitHub o el editor del repositorio.

## Documentación de referencia

El [README](../README.md) es la entrada de 2–4 minutos. Cada guía profundiza una pregunta distinta:

| Documento | Propósito |
| --- | --- |
| [LOCAL_DEVELOPMENT](LOCAL_DEVELOPMENT.md) | Ejecutar Compose, desarrollar con Maven/Vite, configuración y tests. |
| [ARCHITECTURE](ARCHITECTURE.md) | Módulos, modelo, transacciones, concurrencia y diagramas técnicos. |
| [OPERATIONS](OPERATIONS.md) | Semántica del proceso backend: probes, cierre, bootstrap, request ID y timeouts. |
| [OBSERVABILITY](OBSERVABILITY.md) | Scraping, métricas técnicas, cardinalidad y provisioning de dashboards. |
| [SRE](SRE.md) | Métricas transaccionales, SLI/SLO, cobertura, budget y política de alertas. |
| [RUNBOOK](RUNBOOK.md) | Diagnóstico y recuperación según el síntoma; no redefine los SLOs. |
| [KUBERNETES](KUBERNETES.md) | Laboratorio raw: red, recursos, persistencia, probes y experimentos registrados. |
| [HELM](HELM.md) | Flujo recomendado: valores, ownership, instalación, upgrade/rollback y PVCs. |
| [CONTAINER_REGISTRY](CONTAINER_REGISTRY.md) | Contrato CI/OCI/GHCR, promoción, digest, SBOM/provenance y permisos. |
| [ADR cloud](adr/0001-cloud-runtime.md) | Elección ECS/EKS/EC2, alternativas y consecuencias. |
| [AWS_COSTS](AWS_COSTS.md) | Estimación fechada, tarifas, supuestos y controles de costos. |
| [Terraform](../terraform/README.md) | Implementación IaC, validación sin AWS y prerrequisitos de un despliegue opcional. |
| [TERRAFORM_PLAN_REVIEW](TERRAFORM_PLAN_REVIEW.md) | Checkpoint histórico 9A: revisión estática; plan real no ejecutado. |
| [DEPLOYMENT](DEPLOYMENT.md) | Demo Vercel/Render existente y sus límites; distinta del diseño AWS. |
| [STATUS](STATUS.md) | Estado actual seguido por evidencia histórica local/Kubernetes/GitHub. |
| [ROADMAP](ROADMAP.md) | Entregas completadas y mejoras futuras opcionales. |
| [AUDIT](AUDIT.md) | Auditoría funcional histórica y regresiones corregidas. |
| [PORTFOLIO_AUDIT](PORTFOLIO_AUDIT.md) | Diagnóstico del cierre documental y validaciones de portfolio. |
| [PORTFOLIO](PORTFOLIO.md) | Descripciones CV/LinkedIn, pitch y preguntas de entrevista. |
| [Capturas](images/README.md) | Inventario de imágenes reales y checklist manual. |
| [Release notes](RELEASE_NOTES_1.0.0.md) | Borrador v1.0.0 y checklist previo a publicación. |

## Decisiones y presentación

- [Bloqueo de stock](decisiones/Bloqueo%20de%20stock.md): decisión respaldada por código y pruebas.
- [Guion de demostración](portfolio/Guion%20de%20demostracion.md): recorrido funcional de cinco minutos; complementa el pitch técnico.

## Cómo mantener este cuaderno

Antes de trabajar, consultá el estado y el roadmap. Después de un cambio, actualizá su documento de referencia y enlazalo desde las notas relacionadas; evitá mantener copias del estado o de las tareas.

Cuando documentes una decisión, registrá problema, solución actual, alternativas, consecuencias y evidencia en código o pruebas. Distinguí las propuestas de lo implementado. Para un aprendizaje nuevo, explicá el concepto con tus palabras y un ejemplo del proyecto; creá la nota cuando tengas contenido concreto.
