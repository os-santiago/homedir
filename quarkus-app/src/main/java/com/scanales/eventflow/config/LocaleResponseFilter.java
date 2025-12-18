package com.scanales.eventflow.config;

import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;
import java.util.Locale;

@Provider
public class LocaleResponseFilter implements ContainerResponseFilter {

    private static final String COOKIE_NAME = "QP_LOCALE";
    private static final String ATTRIBUTE_NAME = "locale";

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (responseContext.getEntity() instanceof TemplateInstance instance) {
            // 1. Check for cookie
            Cookie localeCookie = requestContext.getCookies().get(COOKIE_NAME);
            if (localeCookie != null) {
                String localeCode = localeCookie.getValue();
                instance.setAttribute(ATTRIBUTE_NAME, Locale.forLanguageTag(localeCode));
            }
            // Note: If no cookie is present, Quarkus/Qute usually defaults to the
            // Accept-Language header matching or the default server locale.
        }
    }
}
