# Localhost Admin API

## Overview

The Localhost Admin API provides a secure administrative interface that:

1. **Only accepts connections from localhost** - Cannot be accessed remotely
2. **Requires Bearer token authentication** - Must provide valid admin token
3. **Provides full administrative access** - Manage CFPs, users, XP, quest classes, etc.

This design ensures that only users with SSH access to the server (or those physically on the server) can use administrative functions.

## Configuration

### 1. Set the Admin Token

On the server, set the `LOCALHOST_ADMIN_TOKEN` environment variable:

```bash
# Generate a secure token
export LOCALHOST_ADMIN_TOKEN=$(openssl rand -hex 32)

# Or set a custom token
export LOCALHOST_ADMIN_TOKEN="your-super-secret-token-here"
```

For production deployment, add this to your systemd service or container environment:

```bash
# In systemd service file
Environment="LOCALHOST_ADMIN_TOKEN=your-token-here"

# In Docker/Podman
podman run -e LOCALHOST_ADMIN_TOKEN="your-token-here" ...
```

### 2. Restart the Application

After setting the token, restart the Homedir application to load the new configuration.

## Usage

### Direct Access (on the server)

If you're logged into the server:

```bash
# Test connection
curl -H "Authorization: Bearer your-token-here" \
  http://localhost:8080/api/localhost-admin/status

# Get all events
curl -H "Authorization: Bearer your-token-here" \
  http://localhost:8080/api/localhost-admin/events

# Update a CFP status
curl -X PUT \
  -H "Authorization: Bearer your-token-here" \
  -H "Content-Type: application/json" \
  -d '{"status":"accepted","note":"Great talk!","version":0}' \
  http://localhost:8080/api/localhost-admin/cfp/devopsdays-santiago-2026/YOUR-CFP-ID/status
```

### Remote Access via SSH Tunnel

From your local machine, create an SSH tunnel:

```bash
# Create tunnel (replace with your server details)
ssh -i ~/.ssh/id_ed25519 -L 18080:localhost:8080 -N -f root@your-server.com

# Now you can access the API on localhost:18080
curl -H "Authorization: Bearer your-token-here" \
  http://localhost:18080/api/localhost-admin/status

# When done, close the tunnel
pkill -f "ssh.*-L 18080:localhost:8080"
```

### Using the Python Script

The repository includes a helper script `update_cfp_localhost_api.py`:

```bash
# From WSL (with tunnel handling built-in)
export LOCALHOST_ADMIN_TOKEN="your-token-here"
python3 update_cfp_localhost_api.py
```

### Using the MCP Server

The MCP server can use the localhost admin API when configured:

```bash
# Set both the API URL and the localhost token
export HOMEDIR_API_URL="http://localhost:8080"
export LOCALHOST_ADMIN_TOKEN="your-token-here"

# Run the MCP server
python3 tools/homedir-cli/mcp_server.py
```

## API Endpoints

### Status & Health

- `GET /api/localhost-admin/status` - Check API status and authentication
- `GET /api/localhost-admin/metrics` - Get platform metrics

### Events

- `GET /api/localhost-admin/events` - List all events

### CFP Management

- `GET /api/localhost-admin/cfp/all` - Get all CFP submissions across events
- `GET /api/localhost-admin/cfp/{eventId}/{cfpId}` - Get specific CFP submission
- `PUT /api/localhost-admin/cfp/{eventId}/{cfpId}/status` - Update CFP status

**Update CFP Status Request Body:**
```json
{
  "status": "accepted",  // accepted, rejected, under_review, waitlisted
  "note": "Optional note explaining the decision",
  "version": 0  // Current version number for optimistic locking
}
```

### User Management

- `GET /api/localhost-admin/users?query=search` - List/search users
- `GET /api/localhost-admin/users/{userId}` - Get specific user
- `POST /api/localhost-admin/users/{userId}/xp` - Add/subtract XP
- `POST /api/localhost-admin/users/{userId}/quest-class` - Update quest class

**Add XP Request Body:**
```json
{
  "amount": 100,  // Positive or negative integer
  "reason": "Completed workshop presentation",
  "questClass": "DEVELOPER"  // Optional: DEVELOPER, DESIGNER, WRITER, ORGANIZER
}
```

**Update Quest Class Request Body:**
```json
{
  "questClass": "DEVELOPER"  // DEVELOPER, DESIGNER, WRITER, ORGANIZER
}
```

## Security Features

1. **Localhost-only**: The API validates that requests come from localhost (127.0.0.1, ::1, localhost)
2. **Bearer token required**: All requests must include `Authorization: Bearer TOKEN` header
3. **Token validation**: The provided token must exactly match `LOCALHOST_ADMIN_TOKEN`
4. **Logging**: All access attempts are logged with IP addresses
5. **Error messages**: Intentionally vague to prevent information disclosure

## Error Responses

```json
// Non-localhost access
{
  "error": "localhost_only",
  "message": "This endpoint only accepts connections from localhost"
}

// Missing token
{
  "error": "missing_token",
  "message": "Bearer token required"
}

// Invalid token
{
  "error": "invalid_token",
  "message": "Invalid admin token"
}

// Token not configured on server
{
  "error": "not_configured",
  "message": "Localhost admin API is not configured"
}
```

## Example: Update Multiple CFPs

```bash
#!/bin/bash
TOKEN="your-token-here"
EVENT_ID="devopsdays-santiago-2026"
API_URL="http://localhost:8080/api/localhost-admin"

CFP_IDS=(
  "36134e03-782d-4265-a4a0-832827ae10a2"
  "3be12da8-cf69-45ad-9754-ab5214381960"
  "d6aa685a-466b-4064-8176-55f1c81c6b3f"
)

for CFP_ID in "${CFP_IDS[@]}"; do
  echo "Updating $CFP_ID..."

  # Get current version
  RESPONSE=$(curl -s -H "Authorization: Bearer $TOKEN" \
    "$API_URL/cfp/$EVENT_ID/$CFP_ID")

  VERSION=$(echo "$RESPONSE" | jq -r '.item.version')

  # Update status
  curl -X PUT \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"status\":\"accepted\",\"note\":\"Charla seleccionada para DevOpsDays Santiago 2026\",\"version\":$VERSION}" \
    "$API_URL/cfp/$EVENT_ID/$CFP_ID/status"

  echo ""
done
```

## Troubleshooting

### "localhost_only" error when using SSH tunnel

Make sure you're connecting to `localhost:PORT`, not `127.0.0.1:PORT` or the tunnel IP.

### "not_configured" error

The `LOCALHOST_ADMIN_TOKEN` environment variable is not set on the server. Set it and restart the application.

### "invalid_token" error

The token you're providing doesn't match the one configured on the server. Verify the token value.

### 401 Unauthorized

Check that you're including the `Authorization: Bearer TOKEN` header in your request.

## Best Practices

1. **Generate strong tokens**: Use `openssl rand -hex 32` or similar
2. **Store tokens securely**: Use environment variables, not config files
3. **Rotate tokens regularly**: Change the token periodically
4. **Close tunnels**: Always close SSH tunnels when done
5. **Audit logs**: Monitor server logs for unauthorized access attempts
6. **Limited exposure**: Keep the token on the server only, don't commit to git

## Production Deployment

For production, add the token to your deployment configuration:

**Systemd:**
```ini
[Service]
Environment="LOCALHOST_ADMIN_TOKEN=token-from-secrets-manager"
```

**Docker/Podman:**
```bash
podman run \
  -e LOCALHOST_ADMIN_TOKEN="$(cat /run/secrets/admin_token)" \
  ...
```

**Kubernetes:**
```yaml
env:
  - name: LOCALHOST_ADMIN_TOKEN
    valueFrom:
      secretKeyRef:
        name: homedir-secrets
        key: localhost-admin-token
```
