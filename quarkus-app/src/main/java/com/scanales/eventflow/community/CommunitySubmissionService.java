package com.scanales.eventflow.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scanales.eventflow.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommunitySubmissionService {
  private static final Logger LOG = Logger.getLogger(CommunitySubmissionService.class);
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
  private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  @Inject PersistenceService persistenceService;
  @Inject CommunityContentService contentService;

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @ConfigProperty(name = "community.content.dir")
  Optional<String> configuredContentDir;

  @ConfigProperty(name = "community.submissions.daily-limit", defaultValue = "5")
  int dailyLimit;

  @ConfigProperty(name = "community.submissions.max-title-length", defaultValue = "140")
  int maxTitleLength;

  @ConfigProperty(name = "community.submissions.max-summary-length", defaultValue = "360")
  int maxSummaryLength;

  @ConfigProperty(name = "community.submissions.max-tags", defaultValue = "8")
  int maxTags;

  private final ObjectMapper yamlMapper =
      new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
  private final ConcurrentHashMap<String, CommunitySubmission> submissions = new ConcurrentHashMap<>();
  private final Object submissionsLock = new Object();
  private volatile long lastKnownSubmissionsMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (submissionsLock) {
      refreshFromDisk(true);
    }
  }

  public CommunitySubmission create(String userId, String userName, CreateRequest request) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      if (request == null) {
        throw new ValidationException("request_required");
      }
      String title = sanitizeText(request.title(), maxTitleLength);
      if (title == null) {
        throw new ValidationException("invalid_title");
      }
      String summary = sanitizeText(request.summary(), maxSummaryLength);
      if (summary == null) {
        throw new ValidationException("invalid_summary");
      }
      String url = sanitizeUrl(request.url());
      if (url == null) {
        throw new ValidationException("invalid_url");
      }
      String source = sanitizeText(request.source(), 80);
      if (source == null) {
        source = "Community member";
      }
      List<String> tags = sanitizeTags(request.tags(), maxTags);
      Instant now = Instant.now();

      if (isRateLimited(normalizedUserId, now)) {
        throw new RateLimitExceededException("daily_submission_limit_reached");
      }
      if (hasDuplicateUrl(url)) {
        throw new DuplicateSubmissionException("duplicate_url_submission");
      }

      String id = UUID.randomUUID().toString();
      CommunitySubmission submission =
          new CommunitySubmission(
              id,
              normalizedUserId,
              sanitizeText(userName, 120),
              title,
              url,
              summary,
              source,
              tags,
              now,
              CommunitySubmissionStatus.PENDING,
              null,
              null,
              null,
              null,
              null);
      submissions.put(id, submission);
      persistSync();
      return submission;
    }
  }

  public List<CommunitySubmission> listMine(String userId, int limit, int offset) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        return List.of();
      }
      Comparator<Instant> createdAtComparator = Comparator.nullsLast(Comparator.reverseOrder());
      return paginate(
          submissions.values().stream()
              .filter(item -> normalizedUserId.equals(item.userId()))
              .sorted(Comparator.comparing(CommunitySubmission::createdAt, createdAtComparator))
              .toList(),
          limit,
          offset);
    }
  }

  public List<CommunitySubmission> listPending(int limit, int offset) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      Comparator<Instant> createdAtComparator = Comparator.nullsLast(Comparator.reverseOrder());
      return paginate(
          submissions.values().stream()
              .filter(item -> item.status() == CommunitySubmissionStatus.PENDING)
              .sorted(Comparator.comparing(CommunitySubmission::createdAt, createdAtComparator))
              .toList(),
          limit,
          offset);
    }
  }

  public Optional<CommunitySubmission> findById(String id) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      if (id == null || id.isBlank()) {
        return Optional.empty();
      }
      return Optional.ofNullable(submissions.get(id));
    }
  }

  public CommunitySubmission approve(String id, String moderator, String note) {
    CommunitySubmission updated;
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      CommunitySubmission current = findOrThrow(id);
      if (current.status() == CommunitySubmissionStatus.APPROVED) {
        return current;
      }
      String normalizedUrl = sanitizeUrl(current.url());
      if (normalizedUrl == null) {
        throw new ValidationException("invalid_submission_data");
      }
      if (hasDuplicateUrl(current.url(), current.id())) {
        throw new DuplicateSubmissionException("duplicate_url_submission");
      }
      Instant now = Instant.now();
      Instant effectiveCreatedAt = current.createdAt() != null ? current.createdAt() : now;
      String contentId = current.contentId() != null ? current.contentId() : "submission-" + current.id();
      String contentFile = current.contentFile();
      if (contentFile == null || contentFile.isBlank()) {
        contentFile = writeApprovedContentFile(current, contentId, effectiveCreatedAt, normalizedUrl);
      } else {
        contentFile =
            ensureApprovedFileExists(current, contentId, contentFile, effectiveCreatedAt, normalizedUrl);
      }

      updated =
          new CommunitySubmission(
              current.id(),
              current.userId(),
              current.userName(),
              current.title(),
              normalizedUrl,
              current.summary(),
              current.source(),
              current.tags(),
              effectiveCreatedAt,
              CommunitySubmissionStatus.APPROVED,
              now,
              sanitizeText(moderator, 320),
              sanitizeText(note, 300),
              contentId,
              contentFile);
      submissions.put(updated.id(), updated);
      persistSync();
    }
    contentService.forceRefreshAsync("submission-approved");
    return updated;
  }

  public CommunitySubmission reject(String id, String moderator, String note) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      CommunitySubmission current = findOrThrow(id);
      if (current.status() == CommunitySubmissionStatus.REJECTED) {
        return current;
      }
      CommunitySubmission updated =
          new CommunitySubmission(
              current.id(),
              current.userId(),
              current.userName(),
              current.title(),
              current.url(),
              current.summary(),
              current.source(),
              current.tags(),
              current.createdAt(),
              CommunitySubmissionStatus.REJECTED,
              Instant.now(),
              sanitizeText(moderator, 320),
              sanitizeText(note, 300),
              current.contentId(),
              current.contentFile());
      submissions.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  public void clearAllForTests() {
    synchronized (submissionsLock) {
      submissions.clear();
      persistSync();
    }
  }

  private void persistSync() {
    try {
      persistenceService.saveCommunitySubmissionsSync(new LinkedHashMap<>(submissions));
      lastKnownSubmissionsMtime = persistenceService.communitySubmissionsLastModifiedMillis();
    } catch (IllegalStateException e) {
      throw new IllegalStateException("failed_to_persist_submission_state", e);
    }
  }

  private void refreshFromDisk(boolean force) {
    long diskMtime = persistenceService.communitySubmissionsLastModifiedMillis();
    if (!force && diskMtime == lastKnownSubmissionsMtime) {
      return;
    }
    submissions.clear();
    submissions.putAll(persistenceService.loadCommunitySubmissions());
    lastKnownSubmissionsMtime = diskMtime;
  }

  private CommunitySubmission findOrThrow(String id) {
    CommunitySubmission submission = submissions.get(id);
    if (submission == null) {
      throw new NotFoundException("submission_not_found");
    }
    return submission;
  }

  private boolean isRateLimited(String userId, Instant now) {
    if (dailyLimit <= 0) {
      return false;
    }
    Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    long count =
        submissions.values().stream()
            .filter(item -> userId.equals(item.userId()))
            .filter(item -> item.createdAt() != null)
            .filter(item -> !item.createdAt().isBefore(startOfDay) && !item.createdAt().isAfter(now))
            .count();
    return count >= dailyLimit;
  }

  private boolean hasDuplicateUrl(String normalizedUrl) {
    return hasDuplicateUrl(normalizedUrl, null);
  }

  private boolean hasDuplicateUrl(String normalizedUrl, String excludeSubmissionId) {
    String canonical = CommunityUrlNormalizer.normalize(normalizedUrl);
    if (canonical == null || canonical.isBlank()) {
      return false;
    }
    if (contentService.containsUrl(canonical)) {
      return true;
    }
    return submissions.values().stream()
        .filter(item -> excludeSubmissionId == null || !excludeSubmissionId.equals(item.id()))
        .filter(item -> item.status() != CommunitySubmissionStatus.REJECTED)
        .map(CommunitySubmission::url)
        .map(CommunityUrlNormalizer::normalize)
        .anyMatch(canonical::equals);
  }

  private static String sanitizeUserId(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return value.isEmpty() ? null : value;
  }

  private static String sanitizeText(String raw, int maxLength) {
    if (raw == null) {
      return null;
    }
    String cleaned = CONTROL.matcher(raw).replaceAll("").trim();
    if (cleaned.isEmpty()) {
      return null;
    }
    if (cleaned.length() > maxLength) {
      cleaned = cleaned.substring(0, maxLength).trim();
    }
    return cleaned.isEmpty() ? null : cleaned;
  }

  private static String sanitizeUrl(String raw) {
    return CommunityUrlNormalizer.normalize(raw);
  }

  private static List<String> sanitizeTags(List<String> rawTags, int maxTags) {
    if (rawTags == null || rawTags.isEmpty() || maxTags <= 0) {
      return List.of();
    }
    Set<String> unique = new LinkedHashSet<>();
    for (String tag : rawTags) {
      String normalized = sanitizeText(tag, 30);
      if (normalized != null) {
        unique.add(normalized);
      }
      if (unique.size() >= maxTags) {
        break;
      }
    }
    return unique.isEmpty() ? List.of() : new ArrayList<>(unique);
  }

  private static List<CommunitySubmission> paginate(
      List<CommunitySubmission> source, int requestedLimit, int requestedOffset) {
    int limit = requestedLimit <= 0 ? 20 : Math.min(requestedLimit, 100);
    int offset = Math.max(0, requestedOffset);
    if (offset >= source.size()) {
      return List.of();
    }
    int end = Math.min(source.size(), offset + limit);
    return source.subList(offset, end);
  }

  private String writeApprovedContentFile(
      CommunitySubmission submission, String contentId, Instant effectiveCreatedAt, String normalizedUrl) {
    try {
      Path dir = resolveContentDir();
      Files.createDirectories(dir);
      if (!Files.isWritable(dir)) {
        throw new IllegalStateException("community_content_dir_not_writable:" + dir.toAbsolutePath());
      }
      String title = sanitizeText(submission.title(), maxTitleLength);
      if (title == null) {
        title = "Community submission " + shortId(submission.id());
      }
      String summary = sanitizeText(submission.summary(), maxSummaryLength);
      if (summary == null) {
        summary = "Submitted by community member.";
      }
      String source = sanitizeText(submission.source(), 80);
      if (source == null) {
        source = "Community member";
      }
      String slug = slugify(title);
      String date = FILE_DATE.format(effectiveCreatedAt.atZone(ZoneOffset.UTC).toLocalDate());
      String fileName = date + "-" + slug + "-" + shortId(submission.id()) + ".yml";
      Path file = dir.resolve(fileName);
      Path tmp = Files.createTempFile(dir, "submission-", ".tmp");
      try {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", contentId);
        payload.put("title", title);
        payload.put("url", normalizedUrl);
        payload.put("summary", summary);
        payload.put("source", source);
        payload.put("created_at", effectiveCreatedAt.toString());
        if (submission.tags() != null && !submission.tags().isEmpty()) {
          payload.put("tags", submission.tags());
        }
        String author = submission.userName() != null ? submission.userName() : submission.userId();
        if (author != null && !author.isBlank()) {
          payload.put("author", author);
        }
        yamlMapper.writeValue(tmp.toFile(), payload);
        moveWithFallback(tmp, file);
      } finally {
        Files.deleteIfExists(tmp);
      }
      return fileName;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      String path = "unknown";
      try {
        path = resolveContentDir().toAbsolutePath().toString();
      } catch (Exception ignored) {
      }
      LOG.errorf(e, "Failed to write approved community content for submission=%s dir=%s", submission.id(), path);
      throw new IllegalStateException("failed_to_write_approved_content:" + path, e);
    }
  }

  private String ensureApprovedFileExists(
      CommunitySubmission submission,
      String contentId,
      String existingFileName,
      Instant effectiveCreatedAt,
      String normalizedUrl) {
    Path file = resolveContentDir().resolve(existingFileName);
    if (Files.exists(file)) {
      return existingFileName;
    }
    LOG.warnf(
        "Approved submission file was missing id=%s file=%s; regenerating",
        submission.id(),
        existingFileName);
    return writeApprovedContentFile(submission, contentId, effectiveCreatedAt, normalizedUrl);
  }

  private static void moveWithFallback(Path source, Path target) throws Exception {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Path resolveContentDir() {
    String configured = configuredContentDir.orElse("").trim();
    if (!configured.isEmpty()) {
      return Paths.get(configured);
    }
    String sysProp = System.getProperty("homedir.data.dir");
    String base = (sysProp != null && !sysProp.isBlank()) ? sysProp : dataDirPath;
    return Paths.get(base, "community", "content");
  }

  private static String slugify(String text) {
    if (text == null || text.isBlank()) {
      return "community-item";
    }
    String normalized =
        text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    if (normalized.isEmpty()) {
      return "community-item";
    }
    return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
  }

  private static String shortId(String id) {
    if (id == null || id.isBlank()) {
      return "item";
    }
    return id.length() <= 8 ? id : id.substring(0, 8);
  }

  public record CreateRequest(String title, String url, String summary, String source, List<String> tags) {}

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
      super(message);
    }
  }

  public static class DuplicateSubmissionException extends RuntimeException {
    public DuplicateSubmissionException(String message) {
      super(message);
    }
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }
}
