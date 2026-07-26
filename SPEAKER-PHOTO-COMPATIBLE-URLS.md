# URLs Compatibles para Fotos de Speakers

## ✅ URLs Que SÍ Funcionan

### GitHub Avatars
- ✅ `https://avatars.githubusercontent.com/u/12345?v=4`
- ✅ `https://githubusercontent.com/...`

**Ejemplo:**
```
https://avatars.githubusercontent.com/u/11546953?v=4
```

### Google Photos (públicas)
- ✅ `https://lh3.googleusercontent.com/...`

**Nota:** Solo funciona si la foto es pública. No requiere autenticación.

### Gravatar
- ✅ `https://gravatar.com/avatar/...`
- ✅ `https://www.gravatar.com/avatar/...`
- ✅ `https://secure.gravatar.com/avatar/...`

**Ejemplo:**
```
https://gravatar.com/avatar/205e460b479e2e5b48aec07710c08d50
```

### Cloudinary
- ✅ `https://res.cloudinary.com/{account}/image/upload/...`

### Imgur
- ✅ `https://i.imgur.com/{id}.jpg`
- ✅ `https://imgur.com/...`

---

## ❌ URLs Que NO Funcionan

### Google Drive
- ❌ `https://drive.google.com/file/d/{id}/view`
- ❌ `https://drive.google.com/uc?id={id}`

**Razón:**
- Requiere autenticación/cookies de Google
- No son URLs directas a imágenes
- Tienen restricciones de compartir/permisos

**Alternativa:** 
1. Sube la foto directamente en tu perfil de HomeDIR
2. O usa Google Photos público (`lh3.googleusercontent.com`)

### Dropbox
- ❌ `https://www.dropbox.com/s/{id}/photo.jpg`

**Razón:** Requiere autenticación, no es URL directa

**Alternativa:** Usa `dl.dropboxusercontent.com` con link público

### URLs privadas/intranet
- ❌ `http://localhost/...`
- ❌ `http://192.168.x.x/...`
- ❌ `http://internal.company.com/...`

**Razón:** Seguridad - previene SSRF (Server-Side Request Forgery)

---

## 📋 Cómo Usar Cada Opción

### Opción 1: Subir Foto Directamente (RECOMENDADO)

1. Ve a tu perfil: https://homedir.opensourcesantiago.io/private/profile
2. Sección "Speaker Profile"
3. Haz click en "Subir Foto"
4. Selecciona imagen (PNG/JPG, máx 20 MB)
5. Guardar

**Ventajas:**
- Siempre disponible
- Optimizada automáticamente
- No depende de servicios externos

### Opción 2: GitHub Avatar

Si tienes cuenta GitHub, automáticamente se puede usar tu avatar:

```
https://avatars.githubusercontent.com/u/{tu-user-id}?v=4
```

Para obtener tu ID:
1. Ve a https://api.github.com/users/{tu-username}
2. Busca el campo `"id"`

### Opción 3: Gravatar

1. Crea cuenta en https://gravatar.com
2. Sube tu foto allí
3. Usa la URL: `https://gravatar.com/avatar/{hash-de-tu-email}`

Para obtener el hash:
```bash
echo -n "tu@email.com" | md5sum
```

### Opción 4: Google Photos Público

1. Sube foto a Google Photos
2. Comparte públicamente
3. Copia la URL que empiece con `lh3.googleusercontent.com`

**Nota:** NO uses la URL de Google Drive, debe ser específicamente de Google Photos.

---

## 🔒 Seguridad

El sistema solo acepta URLs de dominios verificados para prevenir:
- SSRF (Server-Side Request Forgery)
- Inyección de contenido malicioso
- Acceso a recursos internos

**Whitelist de dominios permitidos:**
- `avatars.githubusercontent.com`
- `githubusercontent.com`
- `gravatar.com`
- `www.gravatar.com`
- `secure.gravatar.com`
- `lh3.googleusercontent.com` (Google Photos)
- `cloudinary.com`
- `res.cloudinary.com`
- `imgur.com`
- `i.imgur.com`

---

## ⚡ Performance

### Cache Automático

Todas las URLs externas se cachean automáticamente:
- **TTL:** 7 días
- **Optimización:** Resize a 400x400, calidad 85%
- **Tamaño:** ~50-150 KB (reducción del 90%)

### HTTP Caching

El navegador cachea la foto 7 días:
```
Cache-Control: public, max-age=604800, immutable
ETag: "abc123"
```

**Resultado:** Después del primer request, todos los requests subsecuentes son instant (304 Not Modified).

---

## 🛠️ Troubleshooting

### "Mi foto no aparece"

1. **Verifica que la URL sea pública:**
   - Abre la URL en modo incógnito del navegador
   - Si pide login → NO es pública

2. **Verifica que sea URL directa a imagen:**
   - La URL debe terminar en `.jpg`, `.png`, o ser un CDN conocido
   - NO debe ser una página de visualización

3. **Verifica el dominio:**
   - Debe estar en la whitelist de arriba
   - Solo HTTPS es permitido

### "Mi foto de Google Drive no funciona"

**Solución:**
- Opción A: Sube la foto directamente en HomeDIR (recomendado)
- Opción B: Sube a Google Photos y usa link público `lh3.googleusercontent.com`

### "La foto tarda en cargar la primera vez"

Es normal:
- Primera request: fetch de URL externa (~2-5 segundos)
- Optimización y cache (~1-2 segundos)
- Requests subsecuentes: instant (<5ms desde cache)

---

## 📊 Estadísticas

Ver logs de producción:
```bash
grep "foto_speaker_cacheada" logs/application.log
```

Ejemplo:
```
accion=foto_speaker_cacheada speakerId=sergio.canales@example.com url=https://avatars.githubusercontent.com/... tamano=52480
```

---

## 🚀 Migración de Google Drive URLs

Si actualmente tienes URLs de Google Drive:

### Script de Migración

```bash
# 1. Descargar todas las fotos de Google Drive
for url in $(jq -r '.[] | select(.photoUrl | contains("drive.google")) | .photoUrl' data/speakers.json); do
  # Descargar manualmente cada foto
  echo "Download: $url"
done

# 2. Subir cada foto vía UI de HomeDIR
# (No hay API pública aún, usar UI manual)
```

### O Usar GitHub Avatar

Si el speaker tiene GitHub:
```bash
# Actualizar photoUrl en speakers.json
jq '(.[] | select(.id == "speaker-id") | .photoUrl) = "https://avatars.githubusercontent.com/u/12345"' data/speakers.json
```

---

## 💡 Recomendación

Para máxima confiabilidad y performance:

**🏆 Mejor opción:** Subir foto directamente en HomeDIR
- Control total
- Optimizada automáticamente
- Sin dependencias externas
- Siempre disponible

**🥈 Segunda opción:** GitHub avatar (si aplicable)
- Auto-actualiza cuando cambias en GitHub
- CDN global de GitHub

**🥉 Tercera opción:** Gravatar
- Sincroniza con tu email
- Usado en múltiples sitios
