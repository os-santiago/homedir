package com.scanales.homedir.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import net.coobird.thumbnailator.Thumbnails;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SpeakerPhotoProxyService {

  private static final Set<String> ALLOWED_PHOTO_DOMAINS =
      Set.of(
          "avatars.githubusercontent.com",
          "githubusercontent.com",
          "gravatar.com",
          "www.gravatar.com",
          "secure.gravatar.com",
          "lh3.googleusercontent.com", // Google Photos public URLs
          "cloudinary.com",
          "res.cloudinary.com",
          "imgur.com",
          "i.imgur.com");

  @ConfigProperty(name = "speaker.photo.cache.enabled", defaultValue = "true")
  boolean cacheEnabled;

  @ConfigProperty(name = "speaker.photo.cache.ttl-days", defaultValue = "7")
  int cacheTtlDays;

  @ConfigProperty(name = "speaker.photo.fetch.timeout-seconds", defaultValue = "10")
  int fetchTimeoutSeconds;

  @ConfigProperty(name = "speaker.photo.fetch.max-size-mb", defaultValue = "10")
  int maxSizeMb;

  @ConfigProperty(name = "speaker.photo.optimize.enabled", defaultValue = "true")
  boolean optimizeEnabled;

  @ConfigProperty(name = "speaker.photo.optimize.max-dimension", defaultValue = "400")
  int maxDimension;

  @ConfigProperty(name = "speaker.photo.optimize.quality", defaultValue = "0.85")
  double quality;

  @ConfigProperty(name = "homedir.data-dir", defaultValue = "data")
  String dataDirPath;

  @Inject SpeakerService speakerService;

  public record PhotoResult(Path file, String contentType, String etag) {}

  public PhotoResult getPhoto(String speakerId) {
    com.scanales.homedir.model.Speaker sp = speakerService.getSpeaker(speakerId);

    // 1. Check cache
    if (cacheEnabled && sp != null && sp.getPhotoUrl() != null) {
      PhotoResult cached = getCached(speakerId, sp.getPhotoUrl());
      if (cached != null) {
        Log.debugf("Photo cache hit for speaker %s", speakerId);
        return cached;
      }
    }

    // 2. Check local upload
    PhotoResult uploaded = getUploaded(speakerId);
    if (uploaded != null) {
      Log.debugf("Photo from local upload for speaker %s", speakerId);
      return uploaded;
    }

    // 3. Fetch and cache external URL
    if (sp != null && sp.getPhotoUrl() != null && !sp.getPhotoUrl().isBlank()) {
      PhotoResult fetched = fetchAndCache(speakerId, sp.getPhotoUrl());
      if (fetched != null) {
        Log.debugf("Photo fetched from external URL for speaker %s", speakerId);
        return fetched;
      }
    }

    // 4. Generate default avatar
    Log.debugf("Generating default avatar for speaker %s", speakerId);
    return generateDefaultAvatar(sp != null ? sp.getName() : "?");
  }

  private PhotoResult getCached(String speakerId, String url) {
    try {
      String safeId = speakerId.replaceAll("[^a-zA-Z0-9_.-]", "_");
      String hash = hashUrl(url);
      Path cacheDir = getCacheDir();
      Path cachedFile = safeResolve(cacheDir, safeId + "_" + hash + ".jpg");
      Path metaFile = safeResolve(cacheDir, safeId + "_" + hash + ".meta");

      if (!Files.exists(cachedFile) || !Files.exists(metaFile)) {
        return null;
      }

      // Check TTL
      String metaContent = Files.readString(metaFile);
      String[] parts = metaContent.split("\n");
      if (parts.length >= 2) {
        Instant fetchedAt = Instant.parse(parts[1]);
        long daysSinceFetch = Duration.between(fetchedAt, Instant.now()).toDays();
        if (daysSinceFetch > cacheTtlDays) {
          Log.debugf("Cache expired for speaker %s (age: %d days)", speakerId, daysSinceFetch);
          Files.deleteIfExists(cachedFile);
          Files.deleteIfExists(metaFile);
          return null;
        }
      }

      String etag = generateETag(cachedFile);
      return new PhotoResult(cachedFile, "image/jpeg", etag);

    } catch (Exception e) {
      Log.warnf(e, "Error reading cache for speaker %s", speakerId);
      return null;
    }
  }

  private PhotoResult getUploaded(String speakerId) {
    try {
      String safeSpeakerId = speakerId.replaceAll("[^a-zA-Z0-9_.-]", "_");
      Path uploadsRoot = Paths.get(dataDirPath).resolve("uploads").resolve("speakers");

      String[] extensions = {".png", ".jpg", ".jpeg"};
      for (String ext : extensions) {
        Path file = safeResolve(uploadsRoot, "avatar_" + safeSpeakerId + ext);
        if (Files.exists(file)) {
          String contentType = ext.equals(".png") ? "image/png" : "image/jpeg";
          String etag = generateETag(file);
          return new PhotoResult(file, contentType, etag);
        }
      }
      return null;
    } catch (Exception e) {
      Log.warnf(e, "Error checking uploaded photo for speaker %s", speakerId);
      return null;
    }
  }

  private PhotoResult fetchAndCache(String speakerId, String url) {
    if (!isAllowedUrl(url)) {
      Log.warnf("Rejected photo URL from disallowed domain: %s", url);
      return null;
    }

    try {
      HttpClient client =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(fetchTimeoutSeconds))
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();

      Path tempFile = Files.createTempFile("speaker_photo_", ".tmp");

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(fetchTimeoutSeconds))
              .header("User-Agent", "HomedirBot/1.0 (+https://homedir.opensourcesantiago.io)")
              .build();

      HttpResponse<Path> response =
          client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));

      if (response.statusCode() != 200) {
        Log.warnf("Failed to fetch photo from %s: HTTP %d", url, response.statusCode());
        Files.deleteIfExists(tempFile);
        return null;
      }

      // Check size
      long size = Files.size(tempFile);
      if (size > maxSizeMb * 1024 * 1024) {
        Log.warnf("Photo too large from %s: %d MB", url, size / 1024 / 1024);
        Files.deleteIfExists(tempFile);
        return null;
      }

      // Validate image
      if (!isValidImage(tempFile)) {
        Log.warnf("Invalid image from URL: %s", url);
        Files.deleteIfExists(tempFile);
        return null;
      }

      // Optimize and cache
      if (cacheEnabled) {
        String safeId = speakerId.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String hash = hashUrl(url);
        Path cacheDir = getCacheDir();
        Files.createDirectories(cacheDir);

        Path cachedFile = safeResolve(cacheDir, safeId + "_" + hash + ".jpg");
        Path metaFile = safeResolve(cacheDir, safeId + "_" + hash + ".meta");

        if (optimizeEnabled) {
          optimizeImage(tempFile, cachedFile);
        } else {
          Files.copy(tempFile, cachedFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Save metadata
        String metadata = url + "\n" + Instant.now().toString();
        Files.writeString(metaFile, metadata);

        Files.deleteIfExists(tempFile);

        String etag = generateETag(cachedFile);
        Log.infof(
            "accion=foto_speaker_cacheada speakerId=%s url=%s tamano=%d",
            speakerId, url, Files.size(cachedFile));
        return new PhotoResult(cachedFile, "image/jpeg", etag);
      } else {
        // Return temp file without caching
        String etag = generateETag(tempFile);
        return new PhotoResult(tempFile, "image/jpeg", etag);
      }

    } catch (Exception e) {
      Log.warnf(e, "Error fetching photo from %s for speaker %s", url, speakerId);
      return null;
    }
  }

  private boolean isAllowedUrl(String urlStr) {
    try {
      URI uri = new URI(urlStr);

      // Only HTTPS
      if (!"https".equalsIgnoreCase(uri.getScheme())) {
        Log.warnf("Rejected non-HTTPS URL: %s", urlStr);
        return false;
      }

      String host = uri.getHost();
      if (host == null) {
        return false;
      }

      // Check whitelist
      if (!ALLOWED_PHOTO_DOMAINS.contains(host.toLowerCase())) {
        Log.warnf("Rejected URL from non-whitelisted domain: %s", host);
        return false;
      }

      // Reject private IPs
      InetAddress addr = InetAddress.getByName(host);
      if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()) {
        Log.warnf("Rejected private IP address: %s", addr.getHostAddress());
        return false;
      }

      return true;

    } catch (Exception e) {
      Log.warnf(e, "Error validating URL: %s", urlStr);
      return false;
    }
  }

  private boolean isValidImage(Path file) {
    try {
      byte[] header = Files.readAllBytes(file);
      if (header.length < 12) {
        return false;
      }

      // PNG: 89 50 4E 47 0D 0A 1A 0A
      if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
        return true;
      }

      // JPEG: FF D8 FF
      if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
        return true;
      }

      // WebP: RIFF ... WEBP
      if (header[0] == 0x52
          && header[1] == 0x49
          && header[2] == 0x46
          && header[3] == 0x46
          && header[8] == 0x57
          && header[9] == 0x45
          && header[10] == 0x42
          && header[11] == 0x50) {
        return true;
      }

      return false;

    } catch (Exception e) {
      Log.warnf(e, "Error validating image file: %s", file);
      return false;
    }
  }

  private void optimizeImage(Path source, Path target) throws IOException {
    Thumbnails.of(source.toFile())
        .size(maxDimension, maxDimension)
        .outputFormat("jpg")
        .outputQuality(quality)
        .toFile(target.toFile());
  }

  private PhotoResult generateDefaultAvatar(String name) {
    String initials = getInitials(name);
    String color = getColorFromName(name);

    String svg =
        String.format(
            """
        <svg width="400" height="400" xmlns="http://www.w3.org/2000/svg">
          <rect width="400" height="400" fill="%s"/>
          <text x="50%%" y="50%%" text-anchor="middle" dy=".35em"
                font-family="Arial, sans-serif" font-size="160" fill="white"
                font-weight="bold">%s</text>
        </svg>
        """,
            color, initials);

    try {
      Path tempFile = Files.createTempFile("avatar_default_", ".svg");
      Files.writeString(tempFile, svg);
      String etag = "\"default-" + name.hashCode() + "\"";
      return new PhotoResult(tempFile, "image/svg+xml", etag);
    } catch (IOException e) {
      Log.errorf(e, "Error generating default avatar for %s", name);
      return null;
    }
  }

  private String getInitials(String name) {
    if (name == null || name.isBlank()) {
      return "?";
    }

    String[] parts = name.trim().split("\\s+");
    if (parts.length == 1) {
      return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }

    return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
  }

  private String getColorFromName(String name) {
    String[] colors = {
      "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
      "#00BCD4", "#009688", "#4CAF50", "#FF9800", "#FF5722"
    };

    int hash = Math.abs(name.hashCode());
    return colors[hash % colors.length];
  }

  private String hashUrl(String url) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(url.getBytes());
      return HexFormat.of().formatHex(hash).substring(0, 16);
    } catch (Exception e) {
      return Integer.toHexString(url.hashCode());
    }
  }

  private String generateETag(Path file) {
    try {
      long size = Files.size(file);
      long lastModified = Files.getLastModifiedTime(file).toMillis();
      return "\"" + Long.toHexString(size) + "-" + Long.toHexString(lastModified) + "\"";
    } catch (IOException e) {
      return "\"" + System.currentTimeMillis() + "\"";
    }
  }

  private Path getCacheDir() {
    return Paths.get(dataDirPath).resolve("uploads").resolve("speakers").resolve("cache");
  }

  public void cleanExpiredCache() {
    if (!cacheEnabled) {
      return;
    }

    try {
      Path cacheDir = getCacheDir();
      if (!Files.exists(cacheDir)) {
        return;
      }

      int cleaned = 0;
      var files = Files.list(cacheDir).filter(f -> f.toString().endsWith(".meta")).toList();

      for (Path metaFile : files) {
        try {
          String content = Files.readString(metaFile);
          String[] parts = content.split("\n");
          if (parts.length >= 2) {
            Instant fetchedAt = Instant.parse(parts[1]);
            long daysSinceFetch = Duration.between(fetchedAt, Instant.now()).toDays();

            if (daysSinceFetch > cacheTtlDays) {
              String baseName = metaFile.getFileName().toString().replace(".meta", "");
              Path imageFile = safeResolve(cacheDir, baseName + ".jpg");

              Files.deleteIfExists(metaFile);
              Files.deleteIfExists(imageFile);
              cleaned++;
            }
          }
        } catch (Exception e) {
          Log.warnf(e, "Error processing cache file %s", metaFile);
        }
      }

      if (cleaned > 0) {
        Log.infof("accion=limpieza_cache_fotos archivos_eliminados=%d", cleaned);
      }

    } catch (Exception e) {
      Log.errorf(e, "Error cleaning expired cache");
    }
  }

  private static Path safeResolve(Path base, String child) {
    Path resolved = base.resolve(child).normalize();
    if (!resolved.startsWith(base.normalize())) {
      throw new SecurityException("Path traversal detected: " + child);
    }
    return resolved;
  }
}
