# Baseline de Endurecimiento de Seguridad (VPS + App)

Este baseline define controles de endurecimiento de primer nivel para runtime de HomeDir y operaciones de disaster recovery, sin almacenar datos sensibles en git.

## Alcance

- Controles de runtime en VPS
- Proteccion del canal deploy/webhook
- Verificaciones de seguridad en backup/recuperacion DR

## Automatizacion

Script:

- `platform/scripts/homedir-security-hardening.sh`

Comandos:

```bash
/usr/local/bin/homedir-security-hardening.sh audit
/usr/local/bin/homedir-security-hardening.sh apply
```

`apply` es intencionalmente no destructivo:
- fuerza permisos restrictivos en `/etc/homedir.env`
- restringe directorios de incidentes/backups
- instala baseline de sysctl de red
- habilita `fail2ban` si esta instalado
- reinicia servicio webhook solo cuando esta habilitado

## Controles base

1. Secretos y backups
- `/etc/homedir.env` con propietario `root:root` y modo `600`.
- Artefactos de backup cifrados (`*.age`) y validados con sha256 antes de restore.
- Sin secretos en texto plano ni backups sin cifrar en git.

2. Preparacion de respuesta a incidentes
- Soporte de lock file de escudo de emergencia (`/etc/homedir.incident.lock`).
- Snapshots de incidente en rutas con permisos restringidos.
- Scripts de recuperacion con `umask 077`.

3. Endurecimiento del canal deploy
- Listener webhook enlazado a localhost por defecto.
- Validacion de firma webhook activa (`WEBHOOK_REQUIRE_SIGNATURE=true`).
- Secreto compartido configurado (`WEBHOOK_SHARED_SECRET`).
- Endpoint de estado protegido con token (`WEBHOOK_STATUS_TOKEN`).

4. Endurecimiento del edge
- Snippet nginx de hardening incluido (timeouts, headers, filtro de metodos, limite de body).
- Snippet de incident guard de nginx habilitado para modo mantenimiento de emergencia.

5. Controles de host
- `homedir-auto-deploy.timer` habilitado y activo.
- `homedir-cfp-traffic-guard.timer` habilitado y activo.
- Firewall host activo (`firewalld` o `ufw`).
- Baseline SSH: root restringido y auth por password deshabilitada.

## Integracion en DR

Durante recuperacion DR, incluir:

```bash
/usr/local/bin/homedir-dr-recover.sh ... --apply-hardening
```

Despues de recuperar, ejecutar:

```bash
/usr/local/bin/homedir-security-hardening.sh audit
```

Criterio de salida para simulacro DR:
- Healthcheck y rutas clave en 200.
- `audit` con cero checks FAIL.
