# Checklist de Resiliencia para Go-Live CFP

Este checklist define las puertas minimas de resiliencia operacional antes del go-live de CFP.

Automatizacion de ejecucion:
- Workflow GitHub Actions: `CFP Go-Live Resilience` (`.github/workflows/cfp-go-live-resilience.yml`).

## Gate 1: Merge + Deploy a Produccion + Health

- PR mergeado a `main`.
- Ultimo workflow `Production Release` completado en success.
- Endpoints de salud validados:
  - `/q/health`
  - `/`
  - `/comunidad`
  - `/eventos`
  - `/proyectos`

## Gate 2: Drill de Incidente de Primer Nivel

- Ejecutar comandos de incidente:
  - `homedir-ir-first-level.sh status`
  - `homedir-ir-first-level.sh snapshot`
  - `homedir-ir-first-level.sh shield-on`
  - validar modo mantenimiento externo (HTTP `503`)
  - `homedir-ir-first-level.sh shield-off`
- Mantener evidencia en directorio de incidentes.

## Gate 3: Drill de Preparacion DR

- Generar backup cifrado con `homedir-dr-backup.sh`.
- Verificar extraccion de restore con `homedir-dr-restore.py`.
- Ejecutar `homedir-dr-recover.sh --dry-run --skip-data-restore --apply-hardening`.
- Confirmar ausencia de placeholders de secretos en `/etc/homedir.env`.

## Gate 4: Monitoreo + Alertas Activas

- `homedir-cfp-traffic-guard.timer` habilitado y activo.
- `homedir-cfp-traffic-guard.sh check` en pass.
- Canal de alertas configurado (`homedir-discord-alert.sh`).

## Gate 5: Validacion de Carga Multi-Origen

- Ejecutar probe sintetico CFP/community con simulacion multi-origen.
- Exigir umbrales sobre:
  - tasa de error
  - volumen de `429`
  - volumen de timeouts
- Guardar reporte como artefacto en GitHub Actions.
