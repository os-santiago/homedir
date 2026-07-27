# HomeDir Backup System - Artefactos Versionados

Este directorio contiene todos los artefactos necesarios para restaurar el sistema de backups completo de HomeDir.

## Arquitectura del Sistema de Backups

### 1. Backups Locales en Servidor VPS (72.60.141.165)

**Ubicación:** `/work/data/backups/`

**Componentes:**
- **PersistenceService.java** - Implementa backups automáticos antes de cada save()
- **application.properties** - Configuración de backups universales

**Características:**
- Debouncing: máximo 1 backup cada 5 minutos por archivo
- Retención: 100 archivos por tipo (~8 horas de historia)
- Thread-safe: CAS-based locking
- Auto-pruning: elimina backups antiguos automáticamente

**Archivos protegidos (8 tipos):**
```
/work/data/backups/
├── events/              # 100 archivos × ~20 KB
├── speakers/            # 100 archivos × ~50 KB
├── profiles/            # 100 archivos × ~100 KB
├── economy/             # 100 archivos × ~40 KB
├── challenges/          # 100 archivos × ~40 KB
├── campaigns/           # 100 archivos × ~40 KB
├── community/           # 100 archivos × ~40 KB
├── volunteers/          # 100 archivos × ~40 KB
└── cfp/                 # 120 archivos × ~50 KB (ya existía)
```

**Total:** ~43 MB en servidor

### 2. Sincronización a Google Drive (cada 6 horas)

**Script:** `homedir-vps-backup-reference.ps1` (este directorio)

**Flujo:**
1. Conecta vía SSH a VPS
2. Crea archives comprimidos:
   - `homedir-vps-YYYYMMDD-HHMMSS.tar.gz` - Datos completos de `/work/data/`
   - `homedir-letsencrypt-YYYYMMDD-HHMMSS.tar.gz` - Certificados TLS
3. Extrae snapshot completo del servidor
4. Calcula SHA256 de archives
5. Guarda metadata en JSON
6. Actualiza directorio `latest/`
7. Poda snapshots antiguos (> 7 días)

**Destino:** `G:\My Drive\homedir.opensourcesantiago.io\backups\`

**Estructura:**
```
backups/
├── latest/                          # Último snapshot (actualizado cada 6h)
│   ├── work/data/                   # Datos del servidor
│   │   ├── backups/                 # Backups universales incluidos
│   │   │   ├── events/
│   │   │   ├── speakers/
│   │   │   └── ...
│   │   ├── events.json
│   │   ├── speakers.json
│   │   └── ...
│   ├── etc/
│   ├── usr/local/bin/
│   ├── root/homedir/platform/
│   └── backup-metadata.json
├── snapshot-YYYYMMDD-HHMMSS/       # Snapshots históricos (7 días)
├── archives/                        # Archives comprimidos
│   ├── homedir-vps-*.tar.gz
│   └── homedir-letsencrypt-*.tar.gz
└── logs/                            # Logs de ejecución
    └── backup-*.log
```

**Retención:** 7 días (28 snapshots esperados, ~1.6 GB)

**Configuración Task Scheduler (Windows):**
- Nombre: "Homedir Production Backup"
- Frecuencia: Cada 6 horas
- Comando: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <script-path>`

### 3. Google Drive Cloud Backup

**Sincronización:** Automática (Google Drive desktop app)
- Espacio usado: ~1.6 GB
- Límite gratuito: 15 GB (11% usado)
- Versionado adicional de Google Drive

## Artefactos Incluidos

### Scripts del Servidor VPS

**`homedir-persistent-backup.sh`**
- Script original del servidor (legacy)
- Crea backups tar.gz de `/work/data/`
- Configuración vía `/etc/default/homedir-backup`
- NO se usa actualmente (reemplazado por backups universales)

### Scripts de Sincronización Windows

**`homedir-vps-backup-reference.ps1`** ⚠️ REFERENCIA
- Implementación reconstruida de logs de producción
- Script original (`backup-to-gdrive-hybrid.ps1`) no encontrado
- Puede diferir del script de producción real

### Configuraciones Systemd

**`systemd/homedir-*.service`**
- Servicios systemd del servidor VPS
- Auto-deploy, webhook, update, etc.

**`systemd/homedir-*.timer`**
- Timers para ejecución programada

### Documentación

**`RESTORE-PLAYBOOK.md`**
- Guía paso a paso para restaurar servidor desde backup
- Incluye recuperación de datos, TLS, y servicios

## Instalación/Restauración

### Opción 1: Restaurar Backups Universales (Servidor)

**Requisito:** Servidor VPS operativo con HomeDir desplegado

Los backups universales se activan automáticamente al hacer merge del PR #1254. No requiere instalación manual.

**Verificar funcionamiento:**
```bash
ssh root@72.60.141.165
ls -lh /work/data/backups/events/
ls -lh /work/data/backups/speakers/
```

### Opción 2: Configurar Sincronización Windows → Google Drive

**Requisitos:**
- Windows con PowerShell 5.1+
- WSL instalado
- SSH key configurado en WSL: `~/.ssh/id_ed25519`
- Google Drive desktop instalado
- Acceso SSH al VPS

**Paso 1: Copiar script PowerShell**
```powershell
Copy-Item platform/scripts/backup-system/homedir-vps-backup-reference.ps1 `
    -Destination D:\git\homedir\backup-to-gdrive.ps1
```

**Paso 2: Configurar Task Scheduler**
```powershell
# Abrir Task Scheduler
taskschd.msc

# Crear nueva tarea:
# - Nombre: "Homedir Production Backup"
# - Trigger: Diario, repetir cada 6 horas
# - Action: 
#   - Program: powershell.exe
#   - Arguments: -NoProfile -ExecutionPolicy Bypass -File "D:\git\homedir\backup-to-gdrive.ps1"
# - Conditions: Desmarcar "Start only if on AC power"
```

**Paso 3: Prueba manual**
```powershell
cd D:\git\homedir
.\backup-to-gdrive.ps1
```

**Paso 4: Verificar en Google Drive**
```
G:\My Drive\homedir.opensourcesantiago.io\backups\latest\
```

### Opción 3: Restaurar Servidor Completo desde Backup

Ver **`RESTORE-PLAYBOOK.md`** en este directorio.

## Recuperación de Datos

### Escenario 1: Error Humano (borrado accidental)

**Tiempo de recuperación:** < 5 minutos

**Ventana:** Últimas ~8 horas (100 backups × 5 min)

**Procedimiento:**
```bash
ssh root@72.60.141.165

# Listar backups disponibles
ls -lt /work/data/backups/events/

# Restaurar desde backup específico
cp /work/data/backups/events/events-20260727-120000-542.json \
   /work/data/events.json

# Reiniciar container
podman restart homedir
```

### Escenario 2: Fallo del Servidor

**Tiempo de recuperación:** ~30 minutos

**Ventana:** Últimos 7 días

**Procedimiento:**
```bash
# 1. Provisionar nuevo VPS
# 2. Descargar snapshot de Google Drive
cd "G:\My Drive\homedir.opensourcesantiago.io\backups"
scp -r latest/* root@NEW_VPS_IP:/

# 3. Seguir RESTORE-PLAYBOOK.md
```

### Escenario 3: Disaster Recovery Total

**Tiempo de recuperación:** ~1 hora

**Ventana:** Últimos 7 días

**Procedimiento:**
```bash
# 1. Descargar archive de Google Drive
cd "G:\My Drive\homedir.opensourcesantiago.io\backups\archives"

# 2. Verificar integridad
sha256sum -c homedir-vps-20260727-120000.tar.gz.sha256

# 3. Extraer en nuevo servidor
scp homedir-vps-20260727-120000.tar.gz root@NEW_VPS:/tmp/
ssh root@NEW_VPS
cd /work/data
tar xzf /tmp/homedir-vps-20260727-120000.tar.gz

# 4. Seguir RESTORE-PLAYBOOK.md
```

## Monitoreo y Alertas

### Health Checks Recomendados

**1. Verificar último backup (servidor):**
```bash
ssh root@72.60.141.165 '
  find /work/data/backups/events/ -type f -mmin -10 | wc -l
'
# Debe retornar > 0 (backup en últimos 10 min)
```

**2. Verificar última sincronización (Windows):**
```powershell
$LatestBackup = Get-ChildItem "G:\My Drive\homedir.opensourcesantiago.io\backups\latest\backup-metadata.json"
$Age = (Get-Date) - $LatestBackup.LastWriteTime
if ($Age.TotalHours -gt 12) {
    Write-Warning "Último backup tiene $($Age.TotalHours) horas!"
}
```

**3. Verificar espacio en disco:**
```bash
ssh root@72.60.141.165 'df -h /work/data'
```

### Alertas Sugeridas

**Discord Webhook:**
```bash
# Agregar al final del script PowerShell
if ($Age.TotalHours -gt 12) {
    Invoke-WebHook -Uri $DiscordWebhookUrl -Body @{
        content = "⚠️ Backup de HomeDir tiene $($Age.TotalHours)h de antigüedad"
    }
}
```

## Métricas

### Almacenamiento Actual

| Ubicación | Tamaño | Retención |
|-----------|--------|-----------|
| Servidor VPS `/work/data/backups/` | ~43 MB | ~8 horas |
| Google Drive `backups/` | ~1.6 GB | 7 días |

### Frecuencias

| Tipo | Frecuencia | Debouncing |
|------|------------|------------|
| Backups universales (servidor) | Cada save | 5 min |
| Sincronización Google Drive | Cada 6 horas | N/A |
| Pruning de backups antiguos | Automático | N/A |

### Proyecciones

**Con backups universales activos:**
- Servidor: ~43 MB (estable, auto-pruning)
- Google Drive: ~2.9 GB con 28 snapshots
- Crecimiento anual: ~5 GB con rotación mensual

## Troubleshooting

### "Backup script not found" (Windows)

**Causa:** Script PowerShell original (`backup-to-gdrive-hybrid.ps1`) eliminado o renombrado.

**Solución:** Usar script de referencia incluido:
```powershell
cp platform/scripts/backup-system/homedir-vps-backup-reference.ps1 backup-to-gdrive.ps1
```

### "SSH connection failed" (Windows → VPS)

**Causa:** WSL SSH key no configurado.

**Solución:**
```bash
wsl
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519
ssh-copy-id -i ~/.ssh/id_ed25519 root@72.60.141.165
```

### "Backups directory not created" (Servidor)

**Causa:** PR #1254 no mergeado o código no desplegado.

**Solución:**
```bash
ssh root@72.60.141.165
podman logs homedir | grep "Universal backup system initialized"
# Si no aparece, verificar versión del container
```

### "Old snapshots not pruned" (Google Drive)

**Causa:** Script PowerShell no ejecutándose o pruning deshabilitado.

**Solución:**
```powershell
# Ejecutar pruning manual
$CutoffDate = (Get-Date).AddDays(-7)
Get-ChildItem "G:\My Drive\homedir.opensourcesantiago.io\backups" -Directory |
    Where-Object { $_.Name -match '^snapshot-\d{8}-\d{6}$' } |
    Where-Object { $_.LastWriteTime -lt $CutoffDate } |
    Remove-Item -Recurse -Force
```

## Referencias

- **PR #1254:** Universal Backup System (implementación servidor)
- **BACKUP-SYNC-ANALYSIS.md:** Análisis completo del sistema
- **UNIVERSAL-BACKUP-SYSTEM-DESIGN.md:** Diseño técnico
- **RESTORE-PLAYBOOK.md:** Guía de recuperación

## Changelog

### 2026-07-27
- ✅ Versionados artefactos del sistema de backups
- ✅ Creado script PowerShell de referencia
- ✅ Copiados configs systemd del servidor
- ✅ Documentación completa de arquitectura

### 2026-07-26
- ✅ PR #1254: Implementado universal backup system
- ✅ Agregados 8 tipos de backups automáticos
- ✅ Configuración en application.properties

### 2025-05-28
- Último snapshot sincronizado conocido
- Sistema de sincronización PowerShell operativo

## Contacto

Para preguntas sobre el sistema de backups, revisar:
1. Este README
2. BACKUP-SYNC-ANALYSIS.md (análisis detallado)
3. Logs en `G:\My Drive\homedir.opensourcesantiago.io\backups\logs\`
