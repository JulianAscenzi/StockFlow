{{/* Stable names mirror the raw manifests: one release per namespace. */}}
{{- define "stockflow.labels" -}}
app.kubernetes.io/name: {{ .root.Chart.Name | quote }}
app.kubernetes.io/instance: {{ .root.Release.Name | quote }}
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/component: {{ .component | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service | quote }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version | quote }}
{{- end -}}

{{/* Tags aid browsing; a supplied digest selects immutable image content. */}}
{{- define "stockflow.image" -}}
{{- if .digest -}}
{{- printf "%s@%s" .repository .digest -}}
{{- else -}}
{{- printf "%s:%s" .repository .tag -}}
{{- end -}}
{{- end -}}
