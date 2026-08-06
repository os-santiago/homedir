# Cómo Obtener Listado de Voluntarios

## Opción 1: Usar el Script en el VPS (Recomendado)

### Pasos:

1. **Conectar al VPS:**
   ```bash
   ssh usuario@homedir-vps
   ```

2. **Navegar al directorio del proyecto:**
   ```bash
   cd /ruta/a/homedir
   ```

3. **Ejecutar el script:**
   ```bash
   ./scripts/get_volunteers_with_emails.sh
   ```

4. **Descargar el archivo generado:**
   ```bash
   # Desde tu máquina local:
   scp usuario@vps:/ruta/a/homedir/volunteers-with-emails-*.csv .
   ```

---

## Opción 2: Acceso Directo a los Archivos JSON

Si tienes acceso directo a los archivos:

```bash
# En el VPS
cd /var/lib/homedir

# Ver voluntarios del evento
jq '.[] | select(.event_id == "devopsdays-santiago-2026")' volunteer-submissions.json

# Obtener emails desde profiles.json
jq -r '.[] | select(.userId == "USER_ID_AQUI") | .email' profiles.json
```

---

## Opción 3: Usar el Endpoint API (Cuando se mergee PR #1373)

Una vez que el PR #1373 sea mergeado:

```bash
# Desde el navegador (requiere login como admin):
https://homedir.opensourcesantiago.io/api/private/admin/volunteers/export/devopsdays-santiago-2026.csv

# Desde curl:
curl -u admin@example.com \
  'https://homedir.opensourcesantiago.io/api/private/admin/volunteers/export/devopsdays-santiago-2026.csv' \
  -o volunteers.csv
```

---

## Opción 4: Manual Query (Si tienes acceso SSH)

```bash
# Conectar al VPS
ssh usuario@vps

# Extraer solo nombres y user IDs
jq -r '.[] | 
  select(.event_id == "devopsdays-santiago-2026") | 
  [.applicant_name, .applicant_user_id, .status] | 
  @csv' /var/lib/homedir/volunteer-submissions.json

# Para obtener emails, necesitas cruzar con profiles.json
jq -r --slurpfile volunteers volunteer-submissions.json '
  to_entries[] | 
  select(
    ($volunteers[0] | to_entries[] | 
     select(.value.event_id == "devopsdays-santiago-2026" and 
            .value.applicant_user_id == .key) | 
     .value) != null
  ) | 
  [.value.userId, .value.email, .value.name] | 
  @csv
' /var/lib/homedir/profiles.json
```

---

## Formato del CSV Generado

```csv
Nombre,Email,User ID,Estado,Sobre mí,Razón para unirse,Fecha creación
Juan Pérez,juan@example.com,user123,APPLIED,"Desarrollador...","Quiero contribuir...",2026-01-15T10:30:00Z
María González,maria@example.com,user456,ACCEPTED,"Diseñadora...","Me interesa...",2026-01-16T14:20:00Z
```

---

## Información de Conexión VPS

Según la configuración encontrada:
- **IP VPS:** 72.60.141.165 (histórica, verificar si sigue siendo válida)
- **Usuario:** homedir-sdlc (o el usuario que tengas configurado)
- **Directorio de datos:** `/var/lib/homedir`

---

## Solución de Problemas

### "Archivo no encontrado"
Verificar la ruta del directorio de datos:
```bash
find / -name "volunteer-submissions.json" 2>/dev/null
```

### "Sin permisos"
Ejecutar con el usuario correcto que tenga acceso a `/var/lib/homedir`

### "No hay emails"
El archivo `profiles.json` es necesario para obtener emails. Verificar que existe en el mismo directorio.

