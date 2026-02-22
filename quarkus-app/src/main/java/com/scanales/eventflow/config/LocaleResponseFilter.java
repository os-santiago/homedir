package com.scanales.eventflow.config;

import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;
import java.util.Locale;
import java.util.Set;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

@Provider
public class LocaleResponseFilter {

    private static final String COOKIE_NAME = "QP_LOCALE";
    private static final Set<String> SUPPORTED_LANGS = Set.of("en", "es");
    private static final String DEFAULT_LANG = "en";

    @Inject
    SecurityIdentity identity;

    @Inject
    UserProfileService userProfiles;

    @ServerResponseFilter
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (responseContext.getEntity() instanceof TemplateInstance instance) {
            String localeCode = resolveLocaleCode(requestContext);
            Locale locale = Locale.forLanguageTag(localeCode);
            instance.setLocale(locale);
            instance.data("resolvedLocaleCode", localeCode);
            instance.data("locale", locale);
        }
    }

    private String resolveLocaleCode(ContainerRequestContext requestContext) {
        Cookie localeCookie = requestContext.getCookies().get(COOKIE_NAME);
        String cookieLang = normalizeLang(localeCookie != null ? localeCookie.getValue() : null);
        if (cookieLang != null) {
            return cookieLang;
        }

        String profileLang = normalizeLang(resolveProfileLocale());
        if (profileLang != null) {
            return profileLang;
        }

        for (Locale locale : requestContext.getAcceptableLanguages()) {
            String headerLang = normalizeLang(locale != null ? locale.getLanguage() : null);
            if (headerLang != null) {
                return headerLang;
            }
        }
        return DEFAULT_LANG;
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
        return userProfiles.find(userId.toLowerCase(Locale.ROOT))
                .map(com.scanales.eventflow.model.UserProfile::getPreferredLocale)
                .orElse(null);
    }

    private String normalizeLang(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("-")) {
            normalized = normalized.substring(0, normalized.indexOf('-'));
        }
        return SUPPORTED_LANGS.contains(normalized) ? normalized : null;
    }
}
