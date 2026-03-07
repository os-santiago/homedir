# Respuesta a Incidentes - Primer Nivel (Ataque / DoS)

Este runbook cubre contencion inmediata y recuperacion rapida cuando HomeDir presenta trafico de ataque o sintomas de denegacion de servicio.

## Objetivo

- Proteger disponibilidad e integridad de datos.
- Reducir rapidamente el impacto.
- Recuperar a una version estable con el minimo de pasos.

## Criterios de entrada

Aplicar este flujo si ocurre una o mas de estas condiciones:
- aumento sostenido de 5xx
- colapso de latencia
- saturacion de nginx/podman
- rafagas anormales de trafico con degradacion

## Comandos automatizados

Script:
- `platform/scripts/homedir-ir-first-level.sh`

### 1) Diagnostico rapido

```bash
/usr/local/bin/homedir-ir-first-level.sh status
```

### 2) Contencion (escudo de mantenimiento)

```bash
/usr/local/bin/homedir-ir-first-level.sh shield-on
```

Activa el lock file (`/etc/homedir.incident.lock`) via snippet nginx y entrega pagina de mantenimiento al trafico publico.

### 3) Captura de evidencia

```bash
/usr/local/bin/homedir-ir-first-level.sh snapshot
```

Evidencia en `/var/log/homedir-incident/<timestamp>`.

### 4) Recuperacion

Preferido:

```bash
/usr/local/bin/homedir-ir-first-level.sh recover vX.Y.Z
```

Alternativa:

```bash
/usr/local/bin/homedir-ir-first-level.sh deploy-tag vX.Y.Z
/usr/local/bin/homedir-ir-first-level.sh shield-off
```

### 5) Validacion antes de reabrir

- `/q/health` en 200
- `/`, `/comunidad`, `/eventos`, `/proyectos` en 200
- tasa de error en nivel normal

## Manejo de data sensible

- No incluir `/etc/homedir.env` en evidencias.
- Mantener snapshots con permisos restringidos (`umask 077`).
- Mantener backups y env cifrados (`age`) en repositorios seguros.
- Exigir webhook firmado y no exponer endpoint de estado sin token.

## Acciones posteriores

1. Preservar carpeta de evidencia.
2. Documentar indicadores de ataque y linea de tiempo.
3. Rotar secretos si hay sospecha de compromiso.
4. Ejecutar simulacro DR si hay riesgo de compromiso del host.
5. Ejecutar `/usr/local/bin/homedir-cfp-traffic-guard.sh check` y confirmar umbrales saludables.
6. Ejecutar `/usr/local/bin/homedir-security-hardening.sh audit` antes de cerrar el incidente.
