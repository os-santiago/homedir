# Playbook de Recuperacion ante Desastre (VM preaprovisionada)

Este playbook define una recuperacion rapida y repetible de HomeDir cuando se pierde el VPS y ya existe una VM de reemplazo preaprovisionada.

## Objetivo de recuperacion

- Reconstruir runtime usando:
  - repositorio GitHub (`platform/`)
  - imagenes en Quay
  - backup cifrado del directorio de datos del host
  - archivo de secretos/env desde almacenamiento seguro
- Evitar secretos en git y manejo de backups en texto plano.

Para contencion de primer nivel ante ataque (antes de DR completo), usar:
- [Respuesta a Incidentes - Primer Nivel](incident-response-first-level.md)

## Brechas cerradas

1. Brecha: bootstrap mayormente manual.
   - Mejora: `platform/scripts/homedir-dr-recover.sh` automatiza instalacion + restore + deploy + healthcheck.
2. Brecha: backup sin estandar de cifrado/integridad.
   - Mejora: `platform/scripts/homedir-dr-backup.sh` genera artefactos cifrados + sha256 + metadata.
3. Brecha: restauracion sin validacion robusta de path traversal/symlinks.
   - Mejora: `platform/scripts/homedir-dr-restore.py` extrae de forma segura.
4. Brecha: secuencia DR dispersa.
   - Mejora: un orquestador ejecuta flujo DR end-to-end.
5. Brecha: validaciones de hardening post-recuperacion inconsistentes.
   - Mejora: `platform/scripts/homedir-security-hardening.sh` agrega controles repetibles (`audit` y `apply`).

## Modelo de seguridad

- Secretos:
  - `homedir.env` debe venir de un secret store (nunca desde git).
  - Recomendado para transporte: `*.age`.
- Backups:
  - Recomendado: `*.tar.gz.age` + `*.sha256`.
  - Validar checksum antes de restaurar.
- Canal webhook/deploy:
  - Exigir requests firmadas (`WEBHOOK_REQUIRE_SIGNATURE=true`).
  - Mantener endpoint de estado protegido con token (`WEBHOOK_STATUS_TOKEN`).
- Seguridad local:
  - Scripts DR usan `umask 077`.
  - Archivos temporales descifrados se eliminan.
  - El data dir anterior se conserva como `*.pre-dr-<timestamp>`.

## Creacion de backup (periodico)

```bash
/usr/local/bin/homedir-dr-backup.sh \
  --age-recipient <AGE_PUBLIC_RECIPIENT> \
  --retain-count 28 \
  --output-dir /var/backups/homedir-dr
```

Resultado:
- artefacto cifrado (`.tar.gz.age`)
- archivo de integridad (`.sha256`)
- archivo de metadata (`.metadata.json`)
- poda automatica de backups antiguos por sobre `--retain-count` (`28` por defecto)

## Recuperacion (un comando)

```bash
/usr/local/bin/homedir-dr-recover.sh \
  --env-file /secure/homedir.env.age \
  --age-identity /root/.config/age/keys.txt \
  --backup-file /secure/homedir-data-YYYYMMDDTHHMMSSZ.tar.gz.age \
  --backup-sha256-file /secure/homedir-data-YYYYMMDDTHHMMSSZ.tar.gz.age.sha256 \
  --apply-hardening
```

Flags utiles:
- `--repo-ref <tag|branch>`
- `--deploy-tag vX.Y.Z`
- `--skip-nginx`
- `--enable-webhook`
- `--skip-data-restore`
- `--apply-hardening`

## Checklist de simulacro DR

1. Generar backup con `homedir-dr-backup.sh`.
2. Levantar VM limpia preaprovisionada.
3. Ejecutar `homedir-dr-recover.sh` con env y backup cifrados.
4. Verificar:
   - `/q/health` en 200
   - `/`, `/comunidad`, `/eventos`, `/proyectos` en 200
   - `homedir-cfp-traffic-guard.timer` habilitado y activo
5. Registrar tiempo de recuperacion y hallazgos.
6. Ejecutar `/usr/local/bin/homedir-security-hardening.sh audit` y exigir cero FAIL.
