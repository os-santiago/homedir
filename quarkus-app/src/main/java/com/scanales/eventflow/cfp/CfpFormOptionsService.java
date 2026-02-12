package com.scanales.eventflow.cfp;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CfpFormOptionsService {

  @ConfigProperty(name = "cfp.form.levels", defaultValue = "beginner|Beginner,intermediate|Intermediate,advanced|Advanced")
  String levelsRaw;

  @ConfigProperty(name = "cfp.form.formats", defaultValue = "talk|Talk,lightning-talk|Lightning Talk,workshop|Workshop,panel|Panel")
  String formatsRaw;

  @ConfigProperty(name = "cfp.form.durations", defaultValue = "15|15 min,30|30 min,60|60 min,90|90 min")
  String durationsRaw;

  @ConfigProperty(name = "cfp.form.languages", defaultValue = "en|English,es|Spanish")
  String languagesRaw;

  @ConfigProperty(name = "cfp.form.tracks", defaultValue = "ai-agents-copilots|AI Agents & Copilots in Production,platform-engineering-idp|Platform Engineering & Internal Developer Platforms,cloud-native-security|Cloud Native Security & Supply Chain,developer-experience-innersource|Developer Experience & InnerSource,data-ai-platforms-llmops|Data/AI Platforms & LLMOps")
  String tracksRaw;

  @ConfigProperty(name = "cfp.form.duration-by-format", defaultValue = "talk=30,workshop=90,panel=60,lightning-talk=15")
  String durationByFormatRaw;

  private volatile CfpFormCatalog catalog;
  private volatile Map<String, Integer> durationByFormat;

  @PostConstruct
  void init() {
    catalog = buildCatalog();
    durationByFormat = buildDurationByFormat();
  }

  public CfpFormCatalog catalog() {
    return catalog;
  }

  public Map<String, Integer> durationByFormat() {
    return durationByFormat;
  }

  public Optional<String> normalizeLevel(String raw) {
    return normalizeValue(raw, catalog.levels(), false);
  }

  public Optional<String> normalizeFormat(String raw) {
    return normalizeValue(raw, catalog.formats(), false);
  }

  public Optional<String> normalizeLanguage(String raw) {
    Optional<String> direct = normalizeValue(raw, catalog.languages(), true);
    if (direct.isPresent()) {
      return direct;
    }
    String normalized = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    if (normalized == null || normalized.isBlank()) {
      return Optional.empty();
    }
    int hyphen = normalized.indexOf('-');
    if (hyphen > 0) {
      return normalizeValue(normalized.substring(0, hyphen), catalog.languages(), true);
    }
    int underscore = normalized.indexOf('_');
    if (underscore > 0) {
      return normalizeValue(normalized.substring(0, underscore), catalog.languages(), true);
    }
    return Optional.empty();
  }

  public Optional<String> normalizeTrack(String raw) {
    return normalizeValue(raw, catalog.tracks(), false);
  }

  public Optional<Integer> normalizeDuration(Integer raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String value = String.valueOf(raw);
    boolean allowed = catalog.durations().stream().anyMatch(option -> option.value().equals(value));
    return allowed ? Optional.of(raw) : Optional.empty();
  }

  public Optional<Integer> expectedDurationForFormat(String formatValue) {
    if (formatValue == null || formatValue.isBlank()) {
      return Optional.empty();
    }
    Integer mapped = durationByFormat.get(formatValue.toLowerCase(Locale.ROOT));
    return mapped == null ? Optional.empty() : Optional.of(mapped);
  }

  private static Optional<String> normalizeValue(String raw, List<CfpFormOption> options, boolean lowercase) {
    if (raw == null) {
      return Optional.empty();
    }
    String normalized = raw.trim();
    if (normalized.isBlank()) {
      return Optional.empty();
    }
    if (lowercase) {
      normalized = normalized.toLowerCase(Locale.ROOT);
    }
    for (CfpFormOption option : options) {
      if (option.value().equalsIgnoreCase(normalized)) {
        return Optional.of(option.value());
      }
      if (option.label().equalsIgnoreCase(normalized)) {
        return Optional.of(option.value());
      }
    }
    return Optional.empty();
  }

  private CfpFormCatalog buildCatalog() {
    List<CfpFormOption> levels = parseOptions(levelsRaw, true, 8);
    List<CfpFormOption> formats = parseOptions(formatsRaw, true, 8);
    List<CfpFormOption> durations = parseDurations(durationsRaw, 8);
    List<CfpFormOption> languages = parseOptions(languagesRaw, true, 8);
    List<CfpFormOption> tracks = parseOptions(tracksRaw, true, 5);

    if (levels.isEmpty()) {
      levels = List.of(new CfpFormOption("intermediate", "Intermediate"));
    }
    if (formats.isEmpty()) {
      formats = List.of(new CfpFormOption("talk", "Talk"));
    }
    if (durations.isEmpty()) {
      durations = List.of(new CfpFormOption("30", "30 min"));
    }
    if (languages.isEmpty()) {
      languages = List.of(new CfpFormOption("en", "English"));
    }
    if (tracks.isEmpty()) {
      tracks = List.of(new CfpFormOption("platform-engineering-idp", "Platform Engineering & Internal Developer Platforms"));
    }

    return new CfpFormCatalog(
        List.copyOf(levels),
        List.copyOf(formats),
        List.copyOf(durations),
        List.copyOf(languages),
        List.copyOf(tracks));
  }

  private Map<String, Integer> buildDurationByFormat() {
    Map<String, Integer> mapping = parseDurationByFormat(durationByFormatRaw, 8);
    if (mapping.isEmpty()) {
      mapping.put("talk", 30);
      mapping.put("workshop", 90);
      mapping.put("panel", 60);
      mapping.put("lightning-talk", 15);
    }
    return Map.copyOf(mapping);
  }

  private static List<CfpFormOption> parseOptions(String raw, boolean lowercaseValue, int maxItems) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String[] entries = raw.split(",");
    List<CfpFormOption> result = new ArrayList<>();
    Set<String> uniqueValues = new LinkedHashSet<>();
    for (String entry : entries) {
      if (entry == null) {
        continue;
      }
      String trimmed = entry.trim();
      if (trimmed.isBlank()) {
        continue;
      }
      String value;
      String label;
      int separator = trimmed.indexOf('|');
      if (separator > 0 && separator < trimmed.length() - 1) {
        value = trimmed.substring(0, separator).trim();
        label = trimmed.substring(separator + 1).trim();
      } else {
        value = trimmed;
        label = toLabel(trimmed);
      }
      value = sanitizeValue(value, lowercaseValue);
      label = sanitizeLabel(label);
      if (value == null || label == null || !uniqueValues.add(value)) {
        continue;
      }
      result.add(new CfpFormOption(value, label));
      if (result.size() >= maxItems) {
        break;
      }
    }
    return result;
  }

  private static List<CfpFormOption> parseDurations(String raw, int maxItems) {
    List<CfpFormOption> options = parseOptions(raw, false, maxItems);
    List<CfpFormOption> valid = new ArrayList<>();
    Set<String> uniqueValues = new LinkedHashSet<>();
    for (CfpFormOption option : options) {
      try {
        int value = Integer.parseInt(option.value());
        if (value >= 5 && value <= 240 && uniqueValues.add(option.value())) {
          valid.add(new CfpFormOption(String.valueOf(value), option.label()));
        }
      } catch (NumberFormatException ignored) {
      }
    }
    return valid;
  }

  private static Map<String, Integer> parseDurationByFormat(String raw, int maxItems) {
    Map<String, Integer> result = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) {
      return result;
    }
    String[] entries = raw.split(",");
    for (String entry : entries) {
      if (entry == null) {
        continue;
      }
      String trimmed = entry.trim();
      if (trimmed.isBlank()) {
        continue;
      }
      int separator = trimmed.indexOf('=');
      if (separator <= 0 || separator >= trimmed.length() - 1) {
        continue;
      }
      String format = sanitizeValue(trimmed.substring(0, separator).trim(), true);
      if (format == null) {
        continue;
      }
      try {
        int duration = Integer.parseInt(trimmed.substring(separator + 1).trim());
        if (duration >= 5 && duration <= 240) {
          result.putIfAbsent(format, duration);
        }
      } catch (NumberFormatException ignored) {
      }
      if (result.size() >= maxItems) {
        break;
      }
    }
    return result;
  }

  private static String sanitizeValue(String raw, boolean lowercase) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().replaceAll("\\s+", "-");
    value = value.replaceAll("[^A-Za-z0-9_-]", "");
    if (value.isBlank()) {
      return null;
    }
    if (lowercase) {
      value = value.toLowerCase(Locale.ROOT);
    }
    return value;
  }

  private static String sanitizeLabel(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().replaceAll("\\s+", " ");
    return value.isBlank() ? null : value;
  }

  private static String toLabel(String value) {
    String cleaned = value.replace('-', ' ').replace('_', ' ').trim();
    if (cleaned.isBlank()) {
      return value;
    }
    String[] parts = cleaned.split("\\s+");
    StringBuilder label = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      if (!label.isEmpty()) {
        label.append(' ');
      }
      label.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
      if (part.length() > 1) {
        label.append(part.substring(1).toLowerCase(Locale.ROOT));
      }
    }
    return label.toString();
  }
}