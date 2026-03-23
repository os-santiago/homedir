# Reputation Hub Primary Switch Runbook (espejo)

- [EN canonical: Reputation Hub Primary Switch Runbook](../../en/development/reputation-hub-primary-switch-runbook.md)

Este documento resume el procedimiento seguro para promover `Reputation Hub` como experiencia principal sin perder rollback rapido.

## Alcance

Usar cuando se cambie `reputation.hub.primary.enabled` en produccion.

## Checklist rapido

1. Verificar baseline publico (`/`, `/comunidad`, `/eventos`, `/proyectos` => `200`).
2. Verificar rutas Hub y alias EN->ES.
3. Confirmar diagnostico admin (`/api/private/admin/reputation/phase2/diagnostics`) cuando shadow read este habilitado.
4. Activar switch en `/etc/homedir.env`:
   - `reputation.hub.primary.enabled=true`
5. Reiniciar servicio con el metodo operativo estandar.
6. Validar:
   - redirect de `/comunidad/board` hacia Hub para audiencia elegible
   - submenu sin link conflictivo a Board cuando replacement gate este activo
   - leaderboards y recognized contributions visibles sin errores
   - ejecutar smoke automatizado:
     - `scripts/reputation-hub-smoke.sh https://homedir.opensourcesantiago.io primary-on`
7. Validar anti-abuso basico de reconocimiento:
   - self-recognition => `400 recognition_self_not_allowed`
   - repeticion inmediata => `429 recognition_cooldown_active` o `recognition_already_recorded`

## Rollback

1. `reputation.hub.primary.enabled=false`
2. Reiniciar servicio.
3. Confirmar:
   - `/comunidad/board` vuelve a Board sin redirect forzado
   - submenu vuelve a mostrar `Community Board`
   - smoke publico estable

## Evidencia requerida

- PR + merge commit + checks
- timestamp del cambio de flag
- resultado smoke antes/despues
- muestra de diagnostico `phase2/diagnostics`
- decision final: mantener switch o rollback

## Smoke baseline (flags OFF)

- `scripts/reputation-hub-smoke.sh https://homedir.opensourcesantiago.io baseline-off`
