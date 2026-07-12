# Quick Start: Localhost Admin API

Guía rápida para actualizar CFPs usando la nueva API administrativa.

## Configuración Inicial (una vez)

### 1. En el servidor

```bash
# SSH al servidor
ssh -i ~/.ssh/id_ed25519 root@72.60.141.165

# Generar y configurar el token
export LOCALHOST_ADMIN_TOKEN=$(openssl rand -hex 32)
echo "Token generado: $LOCALHOST_ADMIN_TOKEN"

# Guardar el token para uso permanente
echo "LOCALHOST_ADMIN_TOKEN=$LOCALHOST_ADMIN_TOKEN" >> /etc/environment

# Reiniciar el contenedor de homedir para cargar el token
podman restart homedir

# Verificar que el token esté configurado
podman exec homedir env | grep LOCALHOST_ADMIN_TOKEN
```

### 2. En tu máquina local

```bash
# Guardar el token en tu entorno (usar el token generado arriba)
echo 'export LOCALHOST_ADMIN_TOKEN="el-token-generado-arriba"' >> ~/.bashrc
source ~/.bashrc
```

## Uso: Actualizar CFPs

### Opción 1: Script Python (Recomendado)

```bash
# Desde WSL
cd /mnt/d/git/homedir
export LOCALHOST_ADMIN_TOKEN="tu-token-aqui"
python3 update_cfp_localhost_api.py
```

El script automáticamente:
- Crea un túnel SSH
- Se conecta a la API localhost
- Actualiza los CFPs pendientes
- Cierra el túnel

### Opción 2: Manual via curl

```bash
# 1. Crear túnel SSH
ssh -i ~/.ssh/id_ed25519 -L 18080:localhost:8080 -N -f root@72.60.141.165

# 2. Verificar conexión
curl -H "Authorization: Bearer $LOCALHOST_ADMIN_TOKEN" \
  http://localhost:18080/api/localhost-admin/status

# 3. Obtener CFP
curl -H "Authorization: Bearer $LOCALHOST_ADMIN_TOKEN" \
  http://localhost:18080/api/localhost-admin/cfp/devopsdays-santiago-2026/CFP-ID-AQUI

# 4. Actualizar a ACCEPTED
curl -X PUT \
  -H "Authorization: Bearer $LOCALHOST_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"accepted","note":"Charla seleccionada para DevOpsDays Santiago 2026","version":0}' \
  http://localhost:18080/api/localhost-admin/cfp/devopsdays-santiago-2026/CFP-ID-AQUI/status

# 5. Cerrar túnel
pkill -f "ssh.*-L 18080:localhost:8080"
```

### Opción 3: MCP Server (para uso con Claude Code)

```bash
# Configurar variables de entorno
export HOMEDIR_API_URL="http://localhost:18080"
export LOCALHOST_ADMIN_TOKEN="tu-token-aqui"

# Crear túnel SSH
ssh -i ~/.ssh/id_ed25519 -L 18080:localhost:8080 -N -f root@72.60.141.165

# Ejecutar MCP server
python3 tools/homedir-cli/mcp_server.py

# Ahora puedes usar comandos MCP como:
# - update_cfp_status
# - get_cfp_submission
# - add_user_xp
# - etc.
```

## Estados de CFP Disponibles

- `accepted` - Charla aceptada
- `rejected` - Charla rechazada
- `under_review` - En revisión
- `waitlisted` - Lista de espera

## Troubleshooting

### Error: "localhost_only"
El túnel SSH no está activo o estás usando una IP incorrecta. Asegúrate de conectarte a `localhost:18080`.

### Error: "invalid_token"
El token no coincide. Verifica que `LOCALHOST_ADMIN_TOKEN` sea el mismo en el servidor y tu máquina.

### Error: "not_configured"
El token no está configurado en el servidor. Ejecuta la configuración inicial.

### El túnel no se crea
Verifica que tengas acceso SSH al servidor:
```bash
ssh -i ~/.ssh/id_ed25519 root@72.60.141.165 echo "OK"
```

## Ver logs del servidor

```bash
ssh -i ~/.ssh/id_ed25519 root@72.60.141.165 'podman logs homedir --tail 100'
```

## Documentación Completa

Ver `docs/LOCALHOST_ADMIN_API.md` para documentación completa de todos los endpoints disponibles.
