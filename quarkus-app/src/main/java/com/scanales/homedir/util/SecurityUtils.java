package com.scanales.homedir.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Security utilities for redacting sensitive data in logs and outputs. */
public final class SecurityUtils {

  private static final Set<String> SENSITIVE_PARAM_NAMES =
      Set.of(
          "access_token",
          "id_token",
          "refresh_token",
          "code",
          "state",
          "client_secret",
          "authorization",
          "password",
          "secret",
          "token",
          "key",
          "credentials");

  private static final Pattern TOKEN_PATTERN =
      Pattern.compile(
          "(?i)(bearer\\s+|token[=:]\\s*)([a-zA-Z0-9_\\-\\.]{20,})", Pattern.CASE_INSENSITIVE);

  private static final Pattern KEY_VALUE_PATTERN =
      Pattern.compile(
          "(?i)(access_token|id_token|refresh_token|code|state|client_secret|authorization|password|secret|token|key|credentials)[=:]\\s*([^&\\s,]+)",
          Pattern.CASE_INSENSITIVE);

  private SecurityUtils() {
    // Utility class
  }

  /**
   * Redacts sensitive information from a string, replacing tokens and credentials with a safe
   * placeholder.
   *
   * @param text the text to redact
   * @return the redacted text
   */
  public static String redactSensitiveData(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }

    String redacted = text;

    // Redact bearer tokens and similar patterns
    Matcher tokenMatcher = TOKEN_PATTERN.matcher(redacted);
    redacted = tokenMatcher.replaceAll("$1[REDACTED]");

    // Redact key=value patterns for sensitive parameters
    Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(redacted);
    redacted = kvMatcher.replaceAll("$1=[REDACTED]");

    return redacted;
  }

  /**
   * Redacts the last 4 characters of a token, showing only a preview. Useful for debugging while
   * maintaining security.
   *
   * @param token the token to redact
   * @return the redacted token preview
   */
  public static String redactTokenPreview(String token) {
    if (token == null || token.isBlank()) {
      return "[EMPTY]";
    }
    if (token.length() <= 4) {
      return "[REDACTED]";
    }
    return "..." + token.substring(token.length() - 4);
  }

  /**
   * Checks if a parameter name is sensitive and should be redacted.
   *
   * @param paramName the parameter name to check
   * @return true if the parameter is sensitive
   */
  public static boolean isSensitiveParameter(String paramName) {
    if (paramName == null) {
      return false;
    }
    String lower = paramName.toLowerCase();
    return SENSITIVE_PARAM_NAMES.stream().anyMatch(lower::contains);
  }

  /**
   * Redacts a parameter value if the parameter name is sensitive.
   *
   * @param paramName the parameter name
   * @param value the parameter value
   * @return the original value if not sensitive, otherwise [REDACTED]
   */
  public static String redactIfSensitive(String paramName, String value) {
    if (isSensitiveParameter(paramName)) {
      return "[REDACTED]";
    }
    return value;
  }
}
