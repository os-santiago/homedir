package com.scanales.homedir.config;

import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.LocaleResolver;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.ext.Provider;
import java.util.Locale;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

@Provider
public class LocaleResponseFilter {

  @Inject SecurityIdentity identity;

  @Inject UserProfileService userProfiles;

  @ServerResponseFilter
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    if (responseContext.getEntity() instanceof TemplateInstance instance) {
      String localeCode = resolveLocaleCode(requestContext);
      Locale locale = Locale.forLanguageTag(localeCode);
      instance.setLocale(locale);
      instance.data("resolvedLocaleCode", localeCode);
      instance.data("locale", locale);
    }
  }

  private String resolveLocaleCode(ContainerRequestContext requestContext) {
    String profileLang = normalizeLang(resolveProfileLocale());
    String cookieLang = LocaleResolver.cookieFromRequest(requestContext);
    String pathLang = LocaleResolver.pathFromRequest(requestContext);
    String paramLang = LocaleResolver.paramFromRequest(requestContext);
    return LocaleResolver.resolve(profileLang, paramLang, pathLang, cookieLang,
        requestContext.getAcceptableLanguages());
  }

  private String resolveProfileLocale() {
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
    return userProfiles
        .find(userId.toLowerCase(Locale.ROOT))
        .map(com.scanales.homedir.model.UserProfile::getPreferredLocale)
        .orElse(null);
  }

  private String normalizeLang(String language) {
    return LocaleResolver.normalizeOrNull(language);
  }
}
