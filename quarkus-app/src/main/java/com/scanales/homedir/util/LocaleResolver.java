package com.scanales.homedir.util;

import com.scanales.homedir.service.UserProfileService;
import io.quarkus.arc.Arc;
import io.vertx.ext.web.RoutingContext;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single source of truth for resolving the active UI locale.
 *
 * <p>Replaces the previously duplicated logic in {@link TemplateLocaleUtil} and
 * {@code LocaleResponseFilter}. Both must now resolve through this class so they
 * can never disagree (see epic #1267 — "duplicate locale resolution").
 *
 * <p>Precedence (highest first):
 * <ol>
 *   <li>explicit request parameter {@code ?lang=}</li>
 *   <li>URL path prefix {@code /en/} or {@code /es/}</li>
 *   <li>authenticated user profile preference</li>
 *   <li>the {@code QP_LOCALE} cookie</li>
 *   <li>the {@code Accept-Language} header</li>
 *   <li>default locale {@code en}</li>
 * </ol>
 */
public final class LocaleResolver {

  public static final Set<String> SUPPORTED_LANGS = Set.of("en", "es");
  public static final String DEFAULT_LANG = "en";
  public static final String COOKIE_NAME = "QP_LOCALE";
  public static final String PARAM_NAME = "lang";

  private LocaleResolver() {}

  public static String resolve(
      String profileLocale,
      String explicitParam,
      String pathLocale,
      String cookieLocale,
      List<Locale> acceptableLanguages) {
    String normalized = normalizeOrNull(explicitParam);
    if (normalized != null) {
      return normalized;
    }
    normalized = normalizeOrNull(pathLocale);
    if (normalized != null) {
      return normalized;
    }
    normalized = normalizeOrNull(profileLocale);
    if (normalized != null) {
      return normalized;
    }
    normalized = normalizeOrNull(cookieLocale);
    if (normalized != null) {
      return normalized;
    }
    normalized = normalizeFromAcceptable(acceptableLanguages);
    if (normalized != null) {
      return normalized;
    }
    return DEFAULT_LANG;
  }

  /** Resolve using the request-scoped context (profile, path and param come from Arc). */
  public static String resolveFromCookie(String cookieLocale, HttpHeaders headers) {
    return resolve(
        resolveProfileLocale(),
        resolveParamLocale(),
        resolvePathLocale(),
        cookieLocale,
        headers != null ? headers.getAcceptableLanguages() : List.of());
  }

  public static String resolveFromRequest(ContainerRequestContext requestContext) {
    return resolve(
        resolveProfileLocale(),
        paramFromRequest(requestContext),
        pathFromRequest(requestContext),
        cookieFromRequest(requestContext),
        requestContext.getAcceptableLanguages());
  }

  public static String resolveProfileLocale() {
    try {
      SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
      if (identity == null || identity.isAnonymous()) {
        return null;
      }
      String userId = AdminUtils.getClaim(identity, "email");
      if (userId == null || userId.isBlank()) {
        userId = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
      }
      if (userId == null || userId.isBlank()) {
        return null;
      }
      UserProfileService userProfiles = Arc.container().instance(UserProfileService.class).get();
      if (userProfiles == null) {
        return null;
      }
      return userProfiles
          .find(userId.toLowerCase(Locale.ROOT))
          .map(com.scanales.homedir.model.UserProfile::getPreferredLocale)
          .orElse(null);
    } catch (Exception ignored) {
      return null;
    }
  }

  public static String resolveCookieLocale() {
    return cookieFromArc();
  }

  public static String resolvePathLocale() {
    return pathFromArc();
  }

  public static String resolveParamLocale() {
    return paramFromArc();
  }

  public static String cookieFromRequest(ContainerRequestContext requestContext) {
    if (requestContext == null) {
      return null;
    }
    Cookie cookie = requestContext.getCookies().get(COOKIE_NAME);
    return cookie != null ? cookie.getValue() : null;
  }

  public static String pathFromRequest(ContainerRequestContext requestContext) {
    if (requestContext == null) {
      return null;
    }
    return pathFromPath(requestContext.getUriInfo().getPath());
  }

  public static String paramFromRequest(ContainerRequestContext requestContext) {
    if (requestContext == null) {
      return null;
    }
    try {
      return requestContext.getUriInfo().getQueryParameters().getFirst(PARAM_NAME);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String cookieFromArc() {
    RoutingContext rc = tryRoutingContext();
    if (rc != null && rc.request() != null) {
      io.vertx.core.http.Cookie cookie = rc.request().getCookie(COOKIE_NAME);
      return cookie != null ? cookie.getValue() : null;
    }
    return null;
  }

  private static String pathFromArc() {
    RoutingContext rc = tryRoutingContext();
    if (rc != null && rc.request() != null) {
      return pathFromPath(rc.request().path());
    }
    return null;
  }

  private static String paramFromArc() {
    RoutingContext rc = tryRoutingContext();
    if (rc != null && rc.request() != null) {
      try {
        return rc.request().getParam(PARAM_NAME);
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  private static String pathFromPath(String path) {
    if (path == null) {
      return null;
    }
    if (path.equals("/en") || path.startsWith("/en/")) {
      return "en";
    }
    if (path.equals("/es") || path.startsWith("/es/")) {
      return "es";
    }
    return null;
  }

  private static RoutingContext tryRoutingContext() {
    try {
      return Arc.container().instance(RoutingContext.class).get();
    } catch (Exception ignored) {
      return null;
    }
  }

  public static String normalizeOrNull(String localeCode) {
    if (localeCode == null || localeCode.isBlank()) {
      return null;
    }
    String normalized = localeCode.trim().toLowerCase(Locale.ROOT);
    if (normalized.contains("-")) {
      normalized = normalized.substring(0, normalized.indexOf('-'));
    }
    return SUPPORTED_LANGS.contains(normalized) ? normalized : null;
  }

  private static String normalizeFromAcceptable(List<Locale> locales) {
    if (locales == null) {
      return null;
    }
    for (Locale locale : locales) {
      if (locale == null) {
        continue;
      }
      String normalized = normalizeOrNull(locale.getLanguage());
      if (normalized != null) {
        return normalized;
      }
    }
    return null;
  }
}
