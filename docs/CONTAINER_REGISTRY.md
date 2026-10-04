# Imágenes y GitHub Container Registry — Etapa 7

CI prepara y publica `ghcr.io/<owner-en-minúsculas>/stockflow-backend` y `ghcr.io/<owner-en-minúsculas>/stockflow-frontend`. El owner se deriva de `github.repository_owner`. La relación verificable es commit → tests/build aprobados → artefacto OCI probado → tags → digest. Publicar no despliega: Helm/kind siguen siendo operaciones manuales.

## Eventos y dependencias

El workflow único `.github/workflows/ci.yml` conserva los jobs backend (suite completa), frontend (build) y browser (28 escenarios). `images` requiere backend/frontend; construye las dos imágenes en paralelo. `image-smoke` descarga y prueba ambos artefactos. `publish` requiere backend, frontend, browser, images e image-smoke aprobados; nunca reconstruye la imagen probada.

| Evento | Validación | Publicación |
| --- | --- | --- |
| Pull request | Tests, frontend build, navegador, builds OCI y smoke | Ninguna |
| Push a main | Misma validación | `sha-<SHA completo de 40 caracteres>` |
| Push de tag estable `vX.Y.Z` | Misma validación y comprobación estricta del tag | SHA completo y `X.Y.Z` |
| workflow_dispatch | Misma validación | Ninguna, incluso sobre main/tag |

El filtro de GitHub para tags es un glob, no una expresión regular. El job images rechaza versiones con sufijos o ceros iniciales antes de generar artefactos. No se publican aliases major/minor, prereleases ni latest. `docker/metadata-action@v6` genera SHA largo y semver exacto, con `latest=false`. Reutilizar/mover tags y reconstruir el mismo commit no garantiza conservar contenido: deploys que requieren inmutabilidad usan digest. No borrar/recrear tags de versión.

La concurrencia cancela ejecuciones obsoletas de una misma rama/PR. Las de tags tienen cancel-in-progress=false; diferentes tags tienen grupos distintos. Una publicación de dos paquetes o varios tags no es transaccional: un fallo remoto puede dejar publicación parcial. Consultar ambos artifacts `published-*` y digests antes de promover una release.

## Construcción y permisos

Buildx v0.37.2 usa BuildKit v0.33.1, plataforma linux/amd64 y cache oficial `type=gha`, con scopes separados por componente/arquitectura y mode=max. No hay QEMU ni multi-arch todavía. Las actions usan majors estables, coherentes con la CI previa, nunca main/master. La cache acelera capas; no sustituye tests ni la verificación del artefacto.

Los Dockerfiles fijan bases por digest. Backend conserva multistage JDK/JRE 21, curl para probes, usuario stockflow y JAR ejecutable. `-DskipTests` sólo evita repetir la suite dentro de la construcción: images depende de tests backend aprobados. CI pasa SOURCE_DATE_EPOCH del commit; Maven usa outputTimestamp y el exportador OCI rewrite-timestamp. El build local usa una fecha fija de respaldo. Frontend conserva npm ci/package-lock y usuario node; sigue siendo el runtime Vite del laboratorio, no una imagen de hosting estático de producción. Vercel no cambia.

Esto permite repetir el proceso y trazar el contenido, **no garantiza igualdad bit a bit entre builds independientes**: apt/curl, repositorios de dependencias y metadata temporal de attestations pueden variar. Las bases por digest requieren actualización deliberada; fijarlas no equivale a eliminar vulnerabilidades. El generador SBOM predeterminado mantenido por BuildKit también puede actualizarse. Se conserva su identidad en provenance.

El workflow tiene contents:read. Sólo publish obtiene packages:write además de contents:read. Login oficial `docker/login-action@v4` usa github.actor y secrets.GITHUB_TOKEN; no PAT, secretos adicionales ni credenciales en el repositorio. No hay id-token:write ni servicio de firma externo. [GitHub documenta GITHUB_TOKEN para publicar paquetes](https://docs.github.com/en/packages/managing-github-packages-using-github-actions-workflows/publishing-and-installing-a-package-with-github-actions).

## Artefacto exacto, smoke y digest

Buildx exporta un tar OCI con imagen, SBOM y provenance. `scripts/image-artifact.py` verifica hashes de índice/config/capas, plataforma y vínculo de las attestations al manifiesto ejecutable. Varias referencias de tag deben compartir el mismo índice. Docker 29.8.2 con containerd image store carga ese índice conservando attestations y digest. [Docker explica el almacenamiento de attestations](https://docs.docker.com/build/metadata/attestations/).

`image-smoke` ejecuta `scripts/image-smoke.py`: PostgreSQL 17 temporal en tmpfs, backend readiness/liveness UP, frontend HTTP/React, endpoint protegido 401, login por proxy y dashboard autenticado 200. Se verifican usuarios no root. Usa red interna sin puertos publicados, credenciales aleatorias en archivo privado temporal y stdin; elimina únicamente sus containers/red. No repite toda la suite ni conserva datos de negocio.

publish carga el mismo tar, comprueba digest local, publica sus tags y consulta cada referencia con `docker buildx imagetools inspect`; falla si el digest remoto difiere. El digest registrado es el **índice OCI**, que incluye manifiesto ejecutable y attestations, y sirve directamente para desplegar. El summary muestra repository, tags, commit y digest; el step expone output digest. Artifacts por componente:

- `oci-*`: tar y metadata, retención de un día, sin compresión adicional.
- `image-metadata-*`: image.json, sbom.spdx.json y provenance.json, 30 días.
- `image-smoke-reports`: diagnóstico/resultado, siete días.
- `published-*`: image.json con published=true sólo después de verificar todos sus tags, 30 días.

Estas retenciones no son un archivo permanente de releases. Los tags/artifacts del runner deben revisarse en la ejecución real; descargar evidencia antes de su vencimiento si se necesita conservarla.

## SBOM y provenance

Se generan attestations BuildKit SBOM SPDX y provenance mode=max (SLSA v1 con la versión fijada de BuildKit). Ambas acompañan al índice OCI en el registry y también son artifacts JSON legibles. No se versionan archivos generados. No pasar secretos como build-args: mode=max incluye parámetros de construcción.

Después de la publicación real:

```bash
docker buildx imagetools inspect ghcr.io/OWNER/stockflow-backend@sha256:DIGEST
docker buildx imagetools inspect ghcr.io/OWNER/stockflow-backend@sha256:DIGEST --format '{{json .SBOM}}'
docker buildx imagetools inspect ghcr.io/OWNER/stockflow-backend@sha256:DIGEST --format '{{json .Provenance}}'
```

Reemplazar OWNER/DIGEST por valores reales. [Docker documenta SBOM](https://docs.docker.com/build/metadata/attestations/sbom/). Son attestations del builder, no una firma independiente ni verificación de identidad mediante GitHub Artifact Attestations. No se implementa vulnerability scanning en esta etapa: queda deuda explícita, sin simular resultados. La política propuesta para una etapa posterior es un único scanner fijado, informe completo y bloqueo de HIGH/CRITICAL con fix disponible, con política de excepciones revisable.

## Helm y acceso a GHCR

Chart 0.2.0 agrega digest opcional backend/frontend; si está presente tiene prioridad sobre tag. Valores locales y scripts kind conservan las referencias locales. Tag humano:

```bash
helm upgrade --install stockflow ./helm/stockflow --namespace stockflow --create-namespace \
  -f ./helm/stockflow/values-local.yaml \
  --set backend.image.repository=ghcr.io/OWNER/stockflow-backend \
  --set-string backend.image.tag=sha-SHA_COMPLETO \
  --set frontend.image.repository=ghcr.io/OWNER/stockflow-frontend \
  --set-string frontend.image.tag=sha-SHA_COMPLETO
```

Para promover contenido inmutable añadir ambos digests registrados:

```text
--set-string backend.image.digest=sha256:DIGEST_BACKEND
--set-string frontend.image.digest=sha256:DIGEST_FRONTEND
```

Para volver a tags, limpiar explícitamente ambos image.digest. `helm template` permite revisar las referencias antes del upgrade. Kubernetes mantiene management interno, Secrets externos, probes, recursos y persistencia; no se agrega CD.

La primera publicación GHCR puede tener visibilidad privada: decidir la visibilidad del paquete en GitHub. Un paquete público permite pulls anónimos. Para privados, crear un Secret docker-registry fuera del Chart mediante credenciales adecuadas, y pasar sólo su nombre:

```text
--set 'global.imagePullSecrets[0].name=ghcr-pull'
```

La lista se aplica a todos los workloads. GITHUB_TOKEN del runner no se instala en Kubernetes. Helm no crea/lee credenciales y sus releases almacenan sólo las referencias; no pasar tokens/passwords por values o --set. La autenticación/políticas de paquetes sólo pueden verificarse en GitHub y en el cluster consumidor con el paquete real.

## Camino local kind

El laboratorio no necesita GHCR:

```bash
docker build -t stockflow-backend:local-v7 backend
docker build -t stockflow-frontend:local-v7 frontend
kind load docker-image --name stockflow-lab stockflow-backend:local-v7 stockflow-frontend:local-v7
helm upgrade --install stockflow ./helm/stockflow -n stockflow \
  -f ./helm/stockflow/values-local.yaml \
  --set backend.image.tag=local-v7 --set frontend.image.tag=local-v7
```

El cluster/namespace y Secrets deben existir como explica [HELM](HELM.md). Usar tags nuevos, sin mutar imágenes ya desplegadas. `scripts/k8s-up.sh` conserva build/load/Helm; no publica ni descarga GHCR automáticamente.

## Validación local y límites

```bash
python3 -m unittest discover -s scripts/tests -v
helm lint ./helm/stockflow
helm template stockflow ./helm/stockflow -n stockflow -f ./helm/stockflow/values-local.yaml
python3 scripts/image-smoke.py --backend IMAGEN_LOCAL_BACKEND --frontend IMAGEN_LOCAL_FRONTEND --output /tmp/stockflow-image-smoke
```

Smoke requiere PostgreSQL 17-alpine disponible en Docker. Docker builds, carga OCI/digests, attestations, smoke, tests, Compose y render Helm se comprueban localmente. El YAML/estructura y scripts del workflow se inspeccionan sin instalar act ni linters nuevos. Una ejecución real tras push deberá verificar actions, GHA cache, permisos GHCR, publicación/pull por digest y attestations remotas. Ninguno de esos resultados se presume a partir del build local. Evidencia ejecutada en [STATUS](STATUS.md).
