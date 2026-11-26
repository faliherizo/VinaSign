{{- define "vinasign.image" -}}
{{ required "image.registre est requis" .racine.Values.image.registre }}/vinasign/{{ .nom }}:{{ required "image.etiquette est requise" .racine.Values.image.etiquette }}
{{- end -}}

{{/* Contexte de sécurité commun : compte non privilégié, aucune élévation, racine en lecture seule. */}}
{{- define "vinasign.securiteConteneur" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
runAsNonRoot: true
capabilities: { drop: [ALL] }
{{- end -}}

{{- define "vinasign.repartitionZones" -}}
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: ScheduleAnyway
    labelSelector:
      matchLabels: { app.kubernetes.io/name: {{ . }} }
{{- end -}}
