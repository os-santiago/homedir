# Inventario de Artefactos del Sistema de Backups

Última actualización: 2026-07-27

## Artefactos Versionados en Git

### Ubicación: `platform/scripts/backup-system/`

| Archivo | Origen | Propósito | Estado |
|---------|--------|-----------|--------|
| `README.md` | Creado | Documentación completa del sistema | ✅ Activo |
| `BACKUP-SYNC-ANALYSIS.md` | Creado | Análisis técnico detallado | ✅ Activo |
| `RESTORE-PLAYBOOK.md` | VPS snapshot | Guía de restauración servidor | ✅ Activo |
| `INVENTORY.md` | Creado | Este inventario | ✅ Activo |
| `homedir-persistent-backup.sh` | VPS `/usr/local/bin/` | Script legacy de backup servidor | ⚠️ Legacy |
| `homedir-vps-backup-reference.ps1` | Reconstruido de logs | Script sincronización Windows→GDrive | ⚠️ Referencia |
| `systemd/homedir-auto-deploy.service` | VPS `/etc/systemd/system/` | Auto-deploy desde GitHub | ✅ Activo |
| `systemd/homedir-auto-deploy.timer` | VPS `/etc/systemd/system/` | Timer auto-deploy (cada 5min) | ✅ Activo |
| `systemd/homedir-update.service` | VPS `/etc/systemd/system/` | Update container service | ✅ Activo |
| `systemd/homedir-update.timer` | VPS `/etc/systemd/system/` | Update timer (diario) | ✅ Activo |
| `systemd/homedir-webhook.service` | VPS `/etc/systemd/system/` | GitHub webhook listener | ✅ Activo |
| `systemd/homedir-git-pull.service` | VPS `/etc/systemd/system/` | Git pull service | ⚠️ Dev only |
| `systemd/homedir-git-pull.timer` | VPS `/etc/systemd/system/` | Git pull timer | ⚠️ Dev only |
| `systemd/homedir-dev.service` | VPS `/etc/systemd/system/` | Dev environment service | ⚠️ Dev only |

**Total:** 14 archivos versionados

## Artefactos NO Versionados (requieren backup manual)

### Scripts Producción Windows

| Archivo | Ubicación Esperada | Estado | Acción Requerida |
|---------|-------------------|--------|------------------|
| `backup-to-gdrive-hybrid.ps1` | `D:\git\homedir\` | ❌ No encontrado | Buscar o recrear desde referencia |

### Configuración Task Scheduler

| Tarea | Ubicación | Configuración |
|-------|-----------|---------------|
| "Homedir Production Backup" | Task Scheduler Windows | Ejecuta cada 6h: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\git\homedir\backup-to-gdrive-hybrid.ps1"` |

**Acción:** Exportar tarea desde Task Scheduler:
```powershell
Export-ScheduledTask -TaskName "Homedir Production Backup" | Out-File task-backup.xml
```

### Configuración SSH Keys

| Archivo | Ubicación | Propósito |
|---------|-----------|-----------|
| `id_ed25519` | `/home/scanales/.ssh/` (WSL) | SSH key para acceso VPS |
| `id_ed25519.pub` | `/home/scanales/.ssh/` (WSL) | Public key |

**Acción:** Backup manual (NO versionar keys privadas en Git):
```bash
# Backup de public key (seguro versionar)
cp /home/scanales/.ssh/id_ed25519.pub ~/backup/

# Backup de private key (SOLO copias locales encriptadas)
age -r <recipient> -o id_ed25519.age /home/scanales/.ssh/id_ed25519
```

### Configuración VPS

| Archivo | Ubicación VPS | Propósito | Versionado en Snapshot |
|---------|---------------|-----------|------------------------|
| `/etc/homedir.env` | VPS | Variables de entorno | ✅ Sí (en snapshot) |
| `/etc/letsencrypt/` | VPS | Certificados TLS | ✅ Sí (archive separado) |
| `/etc/nginx/sites-available/homedir.conf` | VPS | Config Nginx | ✅ Sí (en snapshot) |

**Nota:** Estos archivos están incluidos en los snapshots de Google Drive automáticamente.

## Artefactos en Código (Versionados en Git)

### Código Backend

| Archivo | Ubicación | Propósito |
|---------|-----------|-----------|
| `PersistenceService.java` | `quarkus-app/src/main/java/.../service/` | Implementación backups universales |
| `application.properties` | `quarkus-app/src/main/resources/` | Configuración backups |

### Documentación

| Archivo | Ubicación | Propósito |
|---------|-----------|-----------|
| `UNIVERSAL-BACKUP-SYSTEM-DESIGN.md` | Root repo | Diseño técnico sistema backups |

## Snapshots en Google Drive (NO Versionados en Git)

### Ubicación: `G:\My Drive\homedir.opensourcesantiago.io\backups\`

| Tipo | Patrón | Retención | Tamaño |
|------|--------|-----------|--------|
| Snapshots | `snapshot-YYYYMMDD-HHMMSS/` | 7 días | ~100 MB cada uno |
| Archives | `archives/homedir-vps-*.tar.gz` | 7 días | ~1.8 MB cada uno |
| TLS Archives | `archives/homedir-letsencrypt-*.tar.gz` | 7 días | ~12 KB cada uno |
| Latest | `latest/` | Siempre actualizado | ~100 MB |
| Logs | `logs/backup-*.log` | 7 días | ~1 KB cada uno |

**Total en Google Drive:** ~1.6 GB

## Checklist de Restauración

### Restaurar Sistema de Backups en Nueva Máquina Windows

- [ ] Instalar Google Drive desktop
- [ ] Configurar carpeta sync: `G:\My Drive\homedir.opensourcesantiago.io\backups\`
- [ ] Instalar WSL
- [ ] Copiar SSH key a WSL: `/home/scanales/.ssh/id_ed25519`
- [ ] Configurar permisos: `chmod 600 ~/.ssh/id_ed25519`
- [ ] Copiar script PowerShell de referencia a ubicación de producción
- [ ] Crear Task Scheduler: "Homedir Production Backup"
  - Trigger: Cada 6 horas
  - Action: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <script-path>`
- [ ] Ejecutar backup manual de prueba
- [ ] Verificar snapshot en Google Drive

### Restaurar Servidor VPS desde Backup

Ver **`RESTORE-PLAYBOOK.md`**

## Plan de Acción Pendiente

### P0 (Crítico)

- [ ] **Encontrar script PowerShell original** `backup-to-gdrive-hybrid.ps1`
  - Buscar en otras ubicaciones
  - Comparar con script de referencia
  - Versionar el real si se encuentra

- [ ] **Exportar Task Scheduler config**
  ```powershell
  Export-ScheduledTask -TaskName "Homedir Production Backup" | Out-File platform/scripts/backup-system/task-scheduler-backup.xml
  ```

### P1 (Alto)

- [ ] **Agregar health check**
  - Script que verifica última fecha backup
  - Alerta Discord si > 12h
  - Integrar con homedir-discord-alert.sh

- [ ] **Documentar SSH key rotation**
  - Procedimiento para rotar keys
  - Backup encriptado con age

### P2 (Medio)

- [ ] **Crear UI de restore** (AdminBackupRestoreResource)
- [ ] **Agregar métricas** (Grafana dashboard)
- [ ] **Implementar rotación granular** (horaria/diaria/semanal/mensual)

## Notas de Seguridad

### ❌ NUNCA Versionar en Git

- SSH private keys (`id_ed25519`)
- `/etc/homedir.env` (contiene secrets)
- Certificados TLS privados
- Passwords o tokens

### ✅ Seguro Versionar

- Scripts públicos (.sh, .ps1)
- Configuraciones systemd
- Public SSH keys (id_ed25519.pub)
- Documentación
- Playbooks de restore

### 🔐 Backup Encriptado (fuera de Git)

Para secrets y keys:
```bash
# Instalar age
# https://github.com/FiloSottile/age

# Generar recipient key
age-keygen -o backup-key.txt

# Encriptar private key
age -r <public-key-from-backup-key.txt> -o id_ed25519.age /home/scanales/.ssh/id_ed25519

# Guardar backup-key.txt en password manager (1Password, etc)
# Guardar id_ed25519.age en Google Drive (seguro, está encriptado)
```

## Changelog

### 2026-07-27
- ✅ Creado inventario completo de artefactos
- ✅ Identificados 14 archivos versionados
- ✅ Identificados 3 artefactos NO versionados pendientes
- ✅ Documentadas ubicaciones y propósitos
- ✅ Creado plan de acción para gaps

## Referencias

- **README.md** - Guía principal del sistema
- **BACKUP-SYNC-ANALYSIS.md** - Análisis técnico
- **RESTORE-PLAYBOOK.md** - Procedimientos de recuperación
- **PR #1254** - Universal Backup System
