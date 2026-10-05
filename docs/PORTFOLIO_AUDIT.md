# Auditoría de cierre de portfolio — 2026-10-04

Revisión documental y estática desde HEAD `4367a2d`, con cambios previos en `.gitignore`, `STATUS`, guía Terraform y un reporte 9A nuevo. Se preservaron esos cambios y la configuración local. No se hizo staging, commit, push, tag, release ni operación cloud. No se modificó código funcional, migraciones, workflows, dashboards ni infraestructura ejecutable.

## Diagnóstico inicial

- README demasiado extenso para evaluación rápida: producto/MVP dominaba sobre evidencia backend/SRE/platform y repetía operación local. Una explicación de selectores no correspondía al lookup paginado actual.
- Cabecera STATUS desactualizada en Etapa 4 y guías que trataban Kubernetes/Helm como futuros. «Sin commit», «siguiente bloque» y números viejos necesitaban contexto histórico.
- AWS/Terraform técnicamente implementado y validado, pero la preparación pendiente de cuenta/plan podía sugerir un portfolio incompleto. Falta explicar la decisión de no provisionar por costos sin atribuir runtime AWS.
- Faltaba material CV/LinkedIn/entrevista, checklist de imágenes y borrador de release. Había una captura real del producto y diagramas Mermaid, sin capturas versionadas de Grafana/CI/Kubernetes.
- `.gitignore` ya cubría state/planes y outputs principales; faltaban variantes de `.env`, kubeconfig, claves privadas, archivos OCI y caché Python.
- Licencia MIT existente. Primera revisión de enlaces Markdown locales: sin archivos ni anchors rotos. No se detectó necesidad de cambiar arquitectura o agregar herramientas.

## Archivos del cierre

Versionados modificados: `README.md`, `.gitignore`, `docs/ARCHITECTURE.md`, `docs/AWS_COSTS.md`, `docs/DEPLOYMENT.md`, `docs/HELM.md`, `docs/Inicio.md`, `docs/KUBERNETES.md`, `docs/OBSERVABILITY.md`, `docs/OPERATIONS.md`, `docs/ROADMAP.md`, `docs/SRE.md`, `docs/STATUS.md`, `docs/adr/0001-cloud-runtime.md`, `docs/decisiones/Bloqueo de stock.md`, `docs/portfolio/Guion de demostracion.md` y `terraform/README.md`.

Nuevos de este cierre: `docs/LOCAL_DEVELOPMENT.md`, `docs/PORTFOLIO.md`, `docs/PORTFOLIO_AUDIT.md`, `docs/RELEASE_NOTES_1.0.0.md` y `docs/images/README.md`.

`docs/TERRAFORM_PLAN_REVIEW.md` ya era un archivo nuevo previo: sólo se añadió una nota de contexto histórico. Los cambios previos de `.gitignore`, `STATUS` y guía Terraform se conservaron. `RUNBOOK`, `CONTAINER_REGISTRY` y la licencia fueron auditados y no necesitaron cambios.

## Resultado por perspectiva

| Perspectiva | Mejora preparada |
| --- | --- |
| Recruiter técnico | Producto primero, capacidades agrupadas, diagrama de alto nivel y entrada breve. CV, LinkedIn y pitch claramente personales. |
| Backend Engineer | Highlights respaldados por código: transacción comercial, snapshots, locks con refresh, idempotencia y tests reales. Arquitectura técnica permanece disponible. |
| SRE | Experimentos medidos con contexto; liveness/readiness/scraping diferenciados. SLOs provisionales, cobertura parcial, límites de telemetría y falta de backup/HA visibles. |
| Platform Engineer | Reproducción Compose, flujo Helm recomendado frente a raw educativo, promoción del OCI exacto y deployment por digest. AWS diseñado, sin plan real/provisionamiento. |

El volumen de stack se presenta como evidencia de decisiones, no como requisitos para ejecutar el producto: Compose basta. Raw Kubernetes se conserva como referencia educativa, Helm es el flujo recomendado. Las guías especializadas mantienen su propósito y se enlazan desde [Inicio](Inicio.md); los comandos detallados se trasladaron a [LOCAL_DEVELOPMENT](LOCAL_DEVELOPMENT.md). No se borró evidencia útil de STATUS.

## Tests y evidencia

**385 tests backend / 28 escenarios Playwright** siguen consistentes con código y checkpoints. Los XML locales suman 386 porque incluyen un `BrowserE2EIT` de una ejecución separada; excluyendo ese runner, el total habitual es 385, sin fallos/errores/omisiones. El reporte Playwright local contiene 28 esperados, cero omitidos/fallidos/flaky. No se reejecutaron suites de aplicación ni experimentos SRE/Kubernetes durante este cierre documental.

El run remoto enlazado en STATUS corresponde a `5063c4b`, no al HEAD ni al candidato futuro v1.0.0. Salud de servicios, paths `/tmp` y artifacts con retención son evidencia histórica, no estado actual garantizado. No se verificó disponibilidad actual de Vercel/Render ni alertas autenticadas GitHub Security.

## Seguridad y archivos locales

Auditoría estática de archivos versionados/nuevos publicables: sin credenciales reales detectadas. Búsqueda de access keys AWS, tokens GitHub, API keys, JWT literales, claves privadas y URLs con credenciales. Dos URLs candidatas pertenecen a fixtures sintéticas de `DatabaseUrlEnvironmentPostProcessorTest`; no requieren rotación. Búsqueda adicional de patrones fuertes en patches del historial Git accesible: cero coincidencias.

- `.env` y `terraform.tfvars` locales permanecen ignorados; no se copiaron ni imprimieron sus valores.
- `frontend/.env.production` versionado contiene sólo URL pública y switch de autenticación; su excepción explícita no habilita secretos frontend.
- No hay kubeconfig, tfstate, saved plans, claves privadas, reports runtime ni archivos OCI versionados. Outputs locales `target`, `node_modules`, `dist`, reports y `.terraform` quedan ignorados; no se borran archivos personales.
- `.terraform.lock.hcl`, templates `.env.example`/`.env.production.example` y `terraform.tfvars.example` siguen versionables.
- La revisión estática no es un pentest, un scanner de vulnerabilidades ni una afirmación de que GitHub Security carece de alertas.

## Validación de este cierre

- `docker compose --env-file <archivo privado temporal> config --quiet`: aprobado con copia fresca del ejemplo y sustitución de las cuatro variables requeridas. No se usó `.env` del usuario ni se inició/reconstruyó el entorno.
- Doce pruebas existentes de scripts: aprobadas con Helm local disponible; verifican OCI, digest/tag, referencias a Secrets y guardas de despliegue sin contactar clusters.
- `bash -n` de todos los scripts shell: aprobado.
- Verificador temporal de enlaces Markdown relativos: 27 archivos, 164 enlaces, cero errores; aprobado tras crear todos los documentos; incluye rutas codificadas y anchors. No se encontraron broken links preexistentes. Los enlaces del README movidos a guía local/demo se ajustaron a su nueva ubicación.
- Comprobación de reglas `.gitignore`: secretos/outputs ignorados; ejemplos, sources y lockfile versionables.
- `git diff --check`: aprobado; diff completo revisado, incluidos documentos nuevos. Staging vacío.

## Preparación v1.0.0

[Release notes y checklist](RELEASE_NOTES_1.0.0.md) preparados, sin publicación. Producto y portfolio están completos dentro del alcance documentado; el candidato de release todavía requiere un commit autorizado, working tree limpio, CI del SHA final y versionado coherente. Backend sigue `0.0.1-SNAPSHOT`, frontend `0.1.0`, Chart `0.2.0` con appVersion `0.0.1-SNAPSHOT`.

Se optó por enlaces a evidencia y licencia MIT sin badges adicionales: no presentar el run histórico como CI actual ni llenar la cabecera de indicadores decorativos. Las capturas pendientes son una checklist manual, no imágenes falsas. [PORTFOLIO](PORTFOLIO.md) contiene tres bullets CV, párrafo LinkedIn, pitch 60–90 segundos y 18 preguntas de entrevista.

La deuda opcional está concentrada en [ROADMAP](ROADMAP.md#optional-future-work): AWS temporal, scanner/firma, NetworkPolicy/TLS, backup/restore, HPA/capacidad, tracing, CI/CD cloud y multi-arch, entre otras mejoras acotadas. Nada de esto fue provisionado o instalado en este cierre.
