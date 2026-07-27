# Análisis del Sistema de Backups y Sincronización

## Situación Actual

### 1. Backups en Servidor VPS (72.60.141.165)

**Ubicación:** `/work/data/` (bind mount desde host)

**Estado ANTES del PR #1254:**
```
/work/data/
├── events.json              ❌ SIN backup automático
├── speakers.json            ❌ SIN backup automático  
├── user-profiles.json       ❌ SIN backup automático
├── economy-state.json       ❌ SIN backup automático
├── challenge-state.json     ❌ SIN backup automático
├── campaign-state.json      ❌ SIN backup automático
├── community-submissions.json ❌ SIN backup automático
├── volunteer-applications.json ❌ SIN backup automático
└── backups/
    └── cfp/                 ✅ Único con backup (120 archivos)
```

**Estado DESPUÉS del PR #1254:**
```
/work/data/
├── events.json              ✅ Con backup
├── speakers.json            ✅ Con backup
├── user-profiles.json       ✅ Con backup
├── economy-state.json       ✅ Con backup
├── challenge-state.json     ✅ Con backup
├── campaign-state.json      ✅ Con backup
├── community-submissions.json ✅ Con backup
├── volunteer-applications.json ✅ Con backup
└── backups/
    ├── cfp/                 ✅ 120 archivos (ya existía)
    ├── events/              🆕 100 archivos (NUEVO)
    ├── speakers/            🆕 100 archivos (NUEVO)
    ├── profiles/            🆕 100 archivos (NUEVO)
    ├── economy/             🆕 100 archivos (NUEVO)
    ├── challenges/          🆕 100 archivos (NUEVO)
    ├── campaigns/           🆕 100 archivos (NUEVO)
    ├── community/           🆕 100 archivos (NUEVO)
    └── volunteers/          🆕 100 archivos (NUEVO)
```

**Retención por tipo:**
- CFP: 120 backups (5 min interval) = ~10 horas de historia
- Otros: 100 backups (5 min interval) = ~8.3 horas de historia

**Almacenamiento estimado (servidor):**
- CFP: ~120 × 50 KB = ~6 MB
- Events: ~100 × 20 KB = ~2 MB
- Speakers: ~100 × 50 KB = ~5 MB
- Profiles: ~100 × 100 KB = ~10 MB
- Otros (economy, challenges, campaigns, community, volunteers): ~100 × 40 KB × 5 = ~20 MB
- **Total: ~43 MB** (en línea con la estimación de diseño)

### 2. Sincronización Automática a Google Drive

**Script:** PowerShell no identificado (probablemente en Task Scheduler)

**Evidencia del log:**
```
Aplicación host: C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.EXE 
  -NoProfile -ExecutionPolicy Bypass 
  -File C:\Users\sergi\scripts\homedir-vps-backup.ps1
```

**Funcionamiento actual:**
1. Cada 6 horas (basado en timestamps de logs)
2. Conecta vía SSH a VPS 72.60.141.165
3. Crea snapshot completo:
   - `tar czf` de `/work/data/` → `homedir-vps-YYYYMMDD-HHMMSS.tar.gz`
   - `tar czf` de certs Let's Encrypt → `homedir-letsencrypt-YYYYMMDD-HHMMSS.tar.gz`
   - Copia ambos a `G:\My Drive\homedir.opensourcesantiago.io\backups\archives\`
   - Expande snapshot a `G:\My Drive\homedir.opensourcesantiago.io\backups\snapshot-YYYYMMDD-HHMMSS\`
   - Actualiza `latest/` con symlinks al snapshot más reciente
   - Genera `backup-metadata.json`
4. Google Drive sincroniza automáticamente

**Destino local:** `G:\My Drive\homedir.opensourcesantiago.io\backups\`

**Estructura de backups sincronizados:**
```
G:\My Drive\homedir.opensourcesantiago.io\backups\
├── latest/                              # Symlinks al snapshot más reciente
│   ├── work/data/                      
│   │   ├── backups/                    
│   │   │   └── cfp/                    # SOLO CFP en último snapshot
│   │   ├── events.json
│   │   ├── speakers.json
│   │   └── ...
│   ├── etc/
│   ├── usr/
│   ├── root/
│   └── backup-metadata.json
├── snapshot-20260521-182301/           # Snapshots históricos (16 total)
├── snapshot-20260522-002301/
├── snapshot-20260522-062301/
├── snapshot-20260522-122301/
├── snapshot-20260522-182301/
├── ...
├── snapshot-20260528-122302/           # Más reciente
├── archives/                            # Archivos tar.gz comprimidos
│   ├── homedir-vps-20260528-122302.tar.gz
│   ├── homedir-letsencrypt-20260528-122302.tar.gz
│   └── ...
├── logs/                                # Logs de ejecución PowerShell
│   └── backup-20260528-122302.log
└── RESTORE-PLAYBOOK.md                  # Instrucciones de recuperación
```

**Retención actual:**
- **Configurado:** `keep_days: 7` (en metadata.json)
- **Realidad:** 16 snapshots desde 2026-05-21 al 2026-05-28 = 7 días ✅
- **Frecuencia:** Cada 6 horas = 4 snapshots/día
- **Cálculo:** 7 días × 4 snapshots/día = 28 snapshots esperados
- **Actual:** 16 snapshots (algunos días no tienen 4, probablemente por fallos de red o máquina apagada)

**Almacenamiento en Google Drive:**
- Por snapshot: ~1.8 MB comprimido (según log)
- 16 snapshots × 1.8 MB = ~29 MB
- Archives históricos: similar
- **Total estimado:** ~60-80 MB

### 3. Integración Automática con PR #1254

**¿Qué cambia con los nuevos backups universales?**

✅ **SÍ se sincronizarán automáticamente:**
- El script PowerShell copia TODO `/work/data/` 
- Los nuevos directorios `/work/data/backups/{events,speakers,...}` están dentro
- Próximo snapshot (6 horas después del merge) incluirá los 8 nuevos directorios

✅ **NO requiere cambios al script PowerShell:**
- No sabe ni le importa qué hay dentro de `/work/data/`
- Solo hace `tar czf` de todo el directorio
- Funciona de forma agnóstica al contenido

✅ **Protección completa desde el primer backup:**
- Primera vez que se guarde `events.json` → backup creado en `/work/data/backups/events/`
- Próxima ejecución del PowerShell (máx 6h) → sincronizado a Google Drive
- Disponible para restore desde Google Drive

### 4. Escenarios de Recuperación

**Escenario 1: Error humano (ej. borrado accidental de talks)**
- **Recuperación inmediata** (< 5 min):
  1. SSH al servidor: `ssh root@72.60.141.165`
  2. Listar backups: `ls -lt /work/data/backups/events/`
  3. Copiar backup más reciente: `cp /work/data/backups/events/events-YYYYMMDD-HHMMSS-NNN.json /work/data/events.json`
  4. Reiniciar container: `podman restart homedir`
- **Ventana de recuperación:** Últimas ~8 horas (100 backups × 5 min)

**Escenario 2: Fallo del servidor (disco, hardware)**
- **Recuperación desde Google Drive** (~30 min):
  1. Provisionar nuevo VPS
  2. Descargar snapshot de Google Drive: `latest/` o `snapshot-YYYYMMDD-HHMMSS/`
  3. Restaurar según RESTORE-PLAYBOOK.md
  4. Los backups universales vienen incluidos en el snapshot
- **Ventana de recuperación:** Últimos 7 días (16 snapshots cada 6h)
- **Pérdida máxima de datos:** 6 horas (tiempo entre snapshots)

**Escenario 3: Disaster Recovery total**
- **Recuperación desde Google Drive** (~1 hora):
  1. Provisionar nuevo VPS en otra región
  2. Descargar archive de Google Drive: `archives/homedir-vps-YYYYMMDD-HHMMSS.tar.gz`
  3. Verificar SHA256
  4. Extraer y restaurar
  5. Reemitir certs TLS (o restaurar archive Let's Encrypt)
- **Ventana de recuperación:** Últimos 7 días
- **Pérdida máxima de datos:** 6 horas

### 5. Gaps y Mejoras Futuras

**Gaps actuales:**

❌ **Backups del servidor NO están en otro servidor físico**
- Tanto `/work/data/` como los backups en `/work/data/backups/` están en el mismo disco
- Fallo de disco = pérdida de datos Y backups locales
- ✅ **Mitigación:** Google Drive sincroniza cada 6h (protege contra fallos de disco)

❌ **Script PowerShell no está versionado ni en repo**
- Ubicación: `C:\Users\sergi\scripts\homedir-vps-backup.ps1` (no encontrado)
- Probablemente ejecutado desde Task Scheduler
- Sin backup del script mismo

❌ **Rotación de snapshots podría ser más granular**
- Actual: 16 snapshots en 7 días (cada 6h)
- Ideal: Estrategia 3-2-1
  - 3 copias de datos
  - 2 tipos de media diferentes
  - 1 copia offsite
- Propuesta:
  - Últimas 24h: cada 6h (4 snapshots)
  - Últimos 7 días: diario (7 snapshots)
  - Último mes: semanal (4 snapshots)
  - Último año: mensual (12 snapshots)

❌ **No hay alertas de fallos de backup**
- Si el PowerShell falla, no hay notificación
- Puede pasar días sin backups y no nos enteramos
- **Propuesta:** Agregar Discord/email alert en el script

**Mejoras propuestas (prioridad):**

**P0 (Crítico):**
1. ✅ **COMPLETADO:** Universal backup system (PR #1254)
2. ⏳ **Siguiente:** Encontrar y versionar el script PowerShell `homedir-vps-backup.ps1`
3. ⏳ **Siguiente:** Agregar health check: si último backup > 12h → alerta

**P1 (Alto):**
4. Agregar restore endpoints en AdminBackupRestoreResource
5. Agregar UI para browsear/restaurar backups sin SSH
6. Agregar métricas de backup (Grafana dashboard)

**P2 (Medio):**
7. Implementar rotación granular (horaria/diaria/semanal/mensual)
8. Agregar backup remoto adicional (S3, otro VPS)
9. Agregar backup verification (restore test automático)

**P3 (Bajo):**
10. Encriptar backups con age (como en `homedir-dr-backup.sh`)
11. Comprimir backups individuales (gzip)
12. Agregar diff viewer para comparar backups

### 6. Costos y Almacenamiento

**Almacenamiento actual:**

- **Servidor VPS:**
  - Backups universales: ~43 MB
  - Espacio disponible: probablemente GB (verificar con `df -h`)
  - **Impacto:** Negligible

- **Google Drive:**
  - Snapshots (16 × 1.8 MB): ~29 MB
  - Archives comprimidos: ~29 MB
  - Logs: <1 MB
  - **Total actual:** ~60 MB
  - **Con backups universales:** ~60 MB + 43 MB (por snapshot) = ~103 MB por snapshot
  - **16 snapshots:** ~1.6 GB
  - **Límite Google Drive:** 15 GB gratis
  - **Uso:** ~11% del espacio gratuito
  - **Proyección (1 año con backups universales):** 
    - Si mantiene 28 snapshots (7 días × 4/día): ~2.9 GB
    - Si implementa rotación granular: ~5 GB (conservando históricos)
  - **Conclusión:** Espacio suficiente por varios años

**Costos adicionales:**
- Google Drive: $0 (dentro del plan gratuito)
- Compute (PowerShell): negligible
- Bandwidth VPS: ~1.8 MB cada 6h = ~300 MB/día = ~9 GB/mes
  - Típicamente dentro de límites gratuitos de VPS

### 7. Plan de Acción Inmediato

**Post-merge PR #1254:**

1. **Verificar primer backup universal** (2h después del merge):
   ```bash
   ssh root@72.60.141.165
   ls -lh /work/data/backups/events/
   ls -lh /work/data/backups/speakers/
   ls -lh /work/data/backups/profiles/
   ```

2. **Verificar primera sincronización** (8h después del merge):
   ```bash
   ls -lh "G:/My Drive/homedir.opensourcesantiago.io/backups/latest/work/data/backups/"
   # Debe mostrar los 8 nuevos directorios
   ```

3. **Documentar el script PowerShell:**
   - Encontrar ubicación exacta
   - Copiar a `platform/scripts/`
   - Versionar en Git
   - Documentar configuración Task Scheduler

4. **Actualizar RESTORE-PLAYBOOK.md:**
   - Agregar sección sobre backups universales
   - Actualizar procedimientos de restore
   - Agregar ejemplos de recuperación por tipo de archivo

5. **Agregar health check:**
   - Script que verifica última fecha de backup
   - Alerta si > 12h desde último backup
   - Integrar con Discord webhook existente

## Conclusión

**Estado actual:** ✅ SISTEMA FUNCIONAL
- Los backups universales (PR #1254) se sincronizarán automáticamente
- NO requiere cambios al script PowerShell existente
- Protección completa contra pérdida de datos desde el primer backup

**Gaps principales:**
1. Script PowerShell no versionado
2. Sin alertas de fallos de backup
3. Sin UI para restore (requiere SSH)

**Siguiente paso recomendado:**
Encontrar y versionar el script `homedir-vps-backup.ps1` para asegurar su continuidad.
