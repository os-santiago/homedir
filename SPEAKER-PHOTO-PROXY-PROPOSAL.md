# Propuesta: Sistema de Proxy para Fotos de Speakers

## Problema Actual

Las imágenes de speakers actualmente pueden ser:
1. **URLs externas** (Google Drive, GitHub, etc.) - no se pueden cargar directamente por CORS/permisos
2. **URLs locales** (`/speaker/{id}/photo`) - sirven archivos desde `data/uploads/speakers/`

**Problemas identificados:**
- URLs de Google Drive no son públicamente accesibles sin autenticación
- No hay control sobre disponibilidad/vigencia de URLs externas
- Problemas de CORS en navegadores
- No hay optimización de imágenes (tamaño, formato)
- Sin caché controlado
- Riesgo de seguridad: inyección de URLs maliciosas

## Solución Propuesta: Sistema de Proxy + Cache con Optimización

### Arquitectura

```
┌─────────────┐
│   Browser   │
└──────┬──────┘
       │ GET /speaker/{id}/photo
       ▼
┌─────────────────────────────────────┐
│  SpeakerPhotoProxyResource          │
│  (nuevo endpoint)                   │
└──────┬──────────────────────────────┘
       │
       ├─ 1. Check cache local
       │  └─ data/uploads/speakers/cache/{speakerId}.{ext}
       │
       ├─ 2. Si no existe → fetch URL externa
       │  └─ Validar URL (whitelist de dominios)
       │  └─ Download con timeout
       │  └─ Validar imagen (magic bytes)
       │  └─ Optimizar (resize, compress)
       │  └─ Guardar en cache
       │
       └─ 3. Servir imagen desde cache
          └─ Headers: Cache-Control, ETag
```

### Componentes

#### 1. **Whitelist de Dominios Permitidos**

```java
private static final Set<String> ALLOWED_PHOTO_DOMAINS = Set.of(
  "avatars.githubusercontent.com",
  "githubusercontent.com",
  "gravatar.com",
  "cloudinary.com",
  "imgur.com",
  // Solo dominios confiables para fotos públicas
);
```

**Seguridad:**
- Previene SSRF (Server-Side Request Forgery)
- Evita acceso a recursos internos (localhost, IPs privadas)
- Bloquea URLs maliciosas

#### 2. **Sistema de Cache Local**

**Directorio:** `data/uploads/speakers/cache/`

**Naming:** `{speakerId}_{hash}.jpg`
- `speakerId`: ID del speaker
- `hash`: SHA-256 de la URL original (detecta cambios de URL)

**Ventajas:**
- Disponibilidad: foto sigue disponible si URL externa muere
- Performance: no re-fetch cada request
- Control: aplicamos optimización una vez

**TTL:** Configurable (default: 7 días)
- Metadata file: `{speakerId}_{hash}.meta` con timestamp y URL original

#### 3. **Validación y Sanitización**

```java
private boolean isValidImageUrl(String url) {
  // 1. Parse URL
  URI uri = new URI(url);
  
  // 2. Validar protocolo
  if (!"https".equals(uri.getScheme())) return false;
  
  // 3. Validar dominio
  if (!ALLOWED_PHOTO_DOMAINS.contains(uri.getHost())) return false;
  
  // 4. Rechazar IPs privadas
  InetAddress addr = InetAddress.getByName(uri.getHost());
  if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()) return false;
  
  return true;
}

private boolean isValidImage(Path file) {
  // Validar magic bytes (no confiar en extension)
  byte[] header = Files.readAllBytes(file).slice(0, 12);
  
  // PNG: 89 50 4E 47
  if (header[0] == (byte)0x89 && header[1] == 0x50) return true;
  
  // JPEG: FF D8 FF
  if (header[0] == (byte)0xFF && header[1] == (byte)0xD8) return true;
  
  // WebP: 52 49 46 46 ... 57 45 42 50
  if (header[0] == 0x52 && header[8] == 0x57) return true;
  
  return false;
}
```

#### 4. **Optimización de Imágenes**

Usar **Thumbnailator** (biblioteca Java ligera):

```xml
<dependency>
  <groupId>net.coobird</groupId>
  <artifactId>thumbnailator</artifactId>
  <version>0.4.20</version>
</dependency>
```

```java
private Path optimizeImage(Path source, Path target) {
  Thumbnails.of(source.toFile())
    .size(400, 400)              // Max 400x400 (suficiente para avatares)
    .outputFormat("jpg")          // Normalizar a JPG
    .outputQuality(0.85)          // 85% calidad (balance tamaño/calidad)
    .toFile(target.toFile());
  
  return target;
}
```

**Beneficios:**
- Reduce tamaño promedio: 2-5 MB → 50-150 KB
- Formato consistente: JPG optimizado
- Mejora velocidad de carga en páginas con múltiples speakers

#### 5. **Sistema de Fallback en Cascada**

```java
public Response getSpeakerPhoto(String speakerId) {
  Speaker sp = speakerService.getSpeaker(speakerId);
  
  // 1. Intentar cache local (optimizada)
  Path cached = getCachedPhoto(speakerId, sp.getPhotoUrl());
  if (cached != null && Files.exists(cached)) {
    return serveImage(cached);
  }
  
  // 2. Intentar upload local (usuario subió foto)
  Path uploaded = getUploadedPhoto(speakerId);
  if (uploaded != null && Files.exists(uploaded)) {
    return serveImage(uploaded);
  }
  
  // 3. Intentar fetch y cache de URL externa
  if (sp.getPhotoUrl() != null && isValidImageUrl(sp.getPhotoUrl())) {
    try {
      Path fetched = fetchAndCachePhoto(speakerId, sp.getPhotoUrl());
      return serveImage(fetched);
    } catch (Exception e) {
      LOG.warnf("Failed to fetch photo from %s: %s", sp.getPhotoUrl(), e.getMessage());
    }
  }
  
  // 4. Fallback: avatar por defecto (SVG generado)
  return generateDefaultAvatar(sp.getName());
}
```

#### 6. **Avatar por Defecto Generado**

Cuando no hay foto disponible, generar SVG con iniciales:

```java
private Response generateDefaultAvatar(String name) {
  String initials = getInitials(name); // "Sergio Canales" → "SC"
  String color = getColorFromName(name); // Hash del nombre → color consistente
  
  String svg = String.format("""
    <svg width="400" height="400" xmlns="http://www.w3.org/2000/svg">
      <rect width="400" height="400" fill="%s"/>
      <text x="50%%" y="50%%" text-anchor="middle" dy=".35em" 
            font-family="Arial" font-size="160" fill="white" 
            font-weight="bold">%s</text>
    </svg>
    """, color, initials);
  
  return Response.ok(svg).type("image/svg+xml").build();
}
```

**Ventajas:**
- Siempre hay una imagen (mejor UX)
- Identificable por color/iniciales
- Lightweight (SVG inline)

#### 7. **Headers HTTP Optimizados**

```java
private Response serveImage(Path file) {
  String etag = generateETag(file); // Hash del contenido
  
  return Response.ok(file.toFile())
    .type(getContentType(file))
    .header("Cache-Control", "public, max-age=604800, immutable") // 7 días
    .header("ETag", etag)
    .header("Vary", "Accept-Encoding")
    .build();
}
```

**Beneficios:**
- Navegador cachea 7 días
- ETag permite validación condicional (304 Not Modified)
- Reduce bandwidth: ~90% de requests son 304

### Implementación

#### Estructura de Archivos

```
data/
└── uploads/
    └── speakers/
        ├── avatar_{speakerId}.{png|jpg}    # Uploads directos (existente)
        └── cache/                          # Nueva carpeta
            ├── {speakerId}_{hash}.jpg      # Foto optimizada
            └── {speakerId}_{hash}.meta     # Metadata (URL, timestamp)
```

#### Configuración

`application.properties`:
```properties
# Photo proxy settings
speaker.photo.cache.enabled=true
speaker.photo.cache.ttl-days=7
speaker.photo.fetch.timeout-seconds=10
speaker.photo.fetch.max-size-mb=10
speaker.photo.optimize.enabled=true
speaker.photo.optimize.max-dimension=400
speaker.photo.optimize.quality=0.85
```

#### Nuevo Endpoint

Modificar `SpeakerResource.getSpeakerPhoto()`:

```java
@GET
@Path("/{id}/photo")
@PermitAll
public Response getSpeakerPhoto(
    @PathParam("id") String speakerId,
    @HeaderParam("If-None-Match") String ifNoneMatch) {
  
  // Cascade: cache → upload → fetch → default
  PhotoResult result = photoProxyService.getPhoto(speakerId);
  
  // Check ETag (304 Not Modified)
  if (ifNoneMatch != null && ifNoneMatch.equals(result.etag())) {
    return Response.notModified().build();
  }
  
  return Response.ok(result.file().toFile())
    .type(result.contentType())
    .header("Cache-Control", "public, max-age=604800, immutable")
    .header("ETag", result.etag())
    .build();
}
```

#### Servicio de Proxy

`SpeakerPhotoProxyService.java`:

```java
@ApplicationScoped
public class SpeakerPhotoProxyService {
  
  private static final Set<String> ALLOWED_DOMAINS = Set.of(
    "avatars.githubusercontent.com",
    "gravatar.com"
  );
  
  @ConfigProperty(name = "speaker.photo.cache.ttl-days", defaultValue = "7")
  int cacheTtlDays;
  
  @ConfigProperty(name = "speaker.photo.fetch.timeout-seconds", defaultValue = "10")
  int fetchTimeoutSeconds;
  
  @Inject SpeakerService speakerService;
  
  public PhotoResult getPhoto(String speakerId) {
    Speaker sp = speakerService.getSpeaker(speakerId);
    
    // 1. Cache hit
    PhotoResult cached = getCached(speakerId, sp.getPhotoUrl());
    if (cached != null) return cached;
    
    // 2. Local upload
    PhotoResult uploaded = getUploaded(speakerId);
    if (uploaded != null) return uploaded;
    
    // 3. Fetch external
    if (sp.getPhotoUrl() != null) {
      PhotoResult fetched = fetchAndCache(speakerId, sp.getPhotoUrl());
      if (fetched != null) return fetched;
    }
    
    // 4. Default avatar
    return generateDefault(sp.getName());
  }
  
  private PhotoResult fetchAndCache(String speakerId, String url) {
    // Validate URL
    if (!isAllowedUrl(url)) {
      LOG.warnf("Rejected photo URL from disallowed domain: %s", url);
      return null;
    }
    
    try {
      // Fetch with timeout
      HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(fetchTimeoutSeconds))
        .build();
      
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(fetchTimeoutSeconds))
        .header("User-Agent", "HomedirBot/1.0")
        .build();
      
      HttpResponse<Path> response = client.send(
        request, 
        HttpResponse.BodyHandlers.ofFile(getTempPath())
      );
      
      if (response.statusCode() != 200) {
        LOG.warnf("Failed to fetch photo: HTTP %d", response.statusCode());
        return null;
      }
      
      Path temp = response.body();
      
      // Validate image
      if (!isValidImage(temp)) {
        Files.delete(temp);
        LOG.warnf("Invalid image from URL: %s", url);
        return null;
      }
      
      // Optimize and cache
      Path cached = getCachePath(speakerId, url);
      optimizeImage(temp, cached);
      saveMetadata(speakerId, url);
      
      Files.delete(temp);
      
      return new PhotoResult(cached, "image/jpeg", generateETag(cached));
      
    } catch (Exception e) {
      LOG.errorf(e, "Error fetching photo from %s", url);
      return null;
    }
  }
}
```

### Tareas de Mantenimiento

#### 1. **Limpieza Automática de Cache**

Background job (Quarkus Scheduler):

```java
@Scheduled(cron = "0 0 3 * * ?") // 3 AM diario
void cleanExpiredCache() {
  Path cacheDir = getCacheDir();
  
  try (Stream<Path> files = Files.list(cacheDir)) {
    files.filter(f -> f.toString().endsWith(".meta"))
      .forEach(metaFile -> {
        try {
          PhotoMetadata meta = readMetadata(metaFile);
          
          // Expired?
          if (Duration.between(meta.fetchedAt(), Instant.now()).toDays() > cacheTtlDays) {
            Files.deleteIfExists(metaFile);
            Files.deleteIfExists(getImageFile(metaFile));
            LOG.infof("Cleaned expired cache: %s", metaFile.getFileName());
          }
        } catch (Exception e) {
          LOG.warnf("Error cleaning cache file %s: %s", metaFile, e.getMessage());
        }
      });
  }
}
```

#### 2. **Re-validación de URLs**

Endpoint admin para forzar re-fetch:

```java
@POST
@Path("/admin/speakers/{id}/photo/refresh")
@RolesAllowed("admin")
public Response refreshSpeakerPhoto(@PathParam("id") String speakerId) {
  // Delete cache
  deleteCachedPhoto(speakerId);
  
  // Force re-fetch
  photoProxyService.getPhoto(speakerId);
  
  return Response.ok(Map.of("refreshed", true)).build();
}
```

### Métricas y Observabilidad

```java
@Counted(name = "speaker_photo_requests", description = "Photo requests by source")
@Timed(name = "speaker_photo_fetch_duration", description = "Time to fetch photos")
public PhotoResult getPhoto(String speakerId) {
  // Implementation with metrics
  
  if (cached != null) {
    metricsService.increment("speaker.photo.source.cache");
    return cached;
  }
  
  if (uploaded != null) {
    metricsService.increment("speaker.photo.source.upload");
    return uploaded;
  }
  
  if (fetched != null) {
    metricsService.increment("speaker.photo.source.external");
    return fetched;
  }
  
  metricsService.increment("speaker.photo.source.default");
  return defaultAvatar;
}
```

**Dashboard:**
- Cache hit rate
- External fetch failures
- Average fetch time
- Storage usage

### Migración

#### Fase 1: Implementar proxy sin romper existente
- Nuevo `SpeakerPhotoProxyService`
- Mantener lógica actual como fallback
- Flag feature: `speaker.photo.proxy.enabled=false` (off por defecto)

#### Fase 2: Activar gradualmente
- Activar en staging
- Monitorear errores/performance
- Activar en producción con flag

#### Fase 3: Pre-cache fotos existentes
- Script que recorre todos los speakers
- Fetch y cache de URLs externas actuales
- Valida que todas las fotos carguen

#### Fase 4: Cleanup
- Remover código legacy
- Simplificar endpoint

### Costos

**Storage:**
- ~100 speakers × 100 KB/foto = 10 MB
- Negligible

**Bandwidth:**
- Initial fetch: 100 speakers × 2 MB = 200 MB (one-time)
- Subsequent: ~10 MB (optimized)
- Ahorro: ~90% vs fetch directo

**Compute:**
- Optimización: ~500ms/foto (one-time)
- Cache hit: <5ms
- ROI: Positivo después de 2-3 requests por speaker

### Alternativas Consideradas

#### Opción 1: CDN Externo (Cloudinary, ImgIX)
**Pros:** Optimización automática, global CDN
**Cons:** Costo mensual, dependencia externa, vendor lock-in
**Decisión:** NO - over-engineering para escala actual

#### Opción 2: Object Storage (S3, R2)
**Pros:** Escalable, CDN integrado
**Cons:** Costo, complejidad, requiere migración
**Decisión:** NO - filesystem suficiente para volumen actual (<500 speakers)

#### Opción 3: Solo validar URLs en save
**Pros:** Simple
**Cons:** No resuelve CORS, disponibilidad, performance
**Decisión:** NO - no soluciona problema raíz

### Recomendación Final

**Implementar Solución Propuesta:**

✅ **Seguridad:**
- Whitelist de dominios
- Validación de imágenes
- Prevención SSRF

✅ **Performance:**
- Cache local (7 días)
- Optimización automática (90% menos tamaño)
- HTTP caching (ETag, Cache-Control)

✅ **Mantenibilidad:**
- Limpieza automática
- Métricas integradas
- Admin endpoints para troubleshooting

✅ **UX:**
- Siempre muestra algo (fallback SVG)
- Rápido (cache hit <5ms)
- Consistente (formato normalizado)

✅ **Costo:**
- Zero costo adicional (usa filesystem existente)
- Reduce bandwidth (optimización)
- Baja complejidad operacional

### Próximos Pasos

1. Crear PR con implementación base (sin flag)
2. Testing en staging con dataset real
3. Activar en producción
4. Ejecutar script de pre-cache
5. Monitorear 1 semana
6. Remover código legacy

---

**Estimación de desarrollo:** 8-12 horas
**Riesgo:** Bajo (backward compatible, incremental rollout)
**Impacto:** Alto (resuelve problema actual + mejora performance)
