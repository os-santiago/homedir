package com.scanales.eventflow.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.List;
import java.net.URI;

import java.util.regex.Pattern;

import io.quarkus.arc.Arc;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.security.identity.SecurityIdentity;

import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.model.Talk;

@TemplateExtension(namespace = "app")
public class AppTemplateExtensions {

    public static int currentYear() {
        return LocalDate.now().getYear();
    }

    public static String version() {
        try {
            Config config = ConfigProvider.getConfig();
            return config.getOptionalValue("quarkus.application.version", String.class).orElse("dev");
        } catch (Exception e) {
            return "dev";
        }
    }

    public static boolean isAuthenticated() {
        SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
        return identity != null && !identity.isAnonymous();
    }

    public static boolean isAdmin() {
        SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
        return AdminUtils.isAdmin(identity);
    }

    /** Simple URL validation for http/https links. */
    public static boolean validUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equals("http") || scheme.equals("https"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Basic email validation. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static boolean validEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /** Returns a human-readable state for the given talk based on current time. */
    public static String talkState(Talk t) {
        if (t == null || t.getStartTime() == null) {
            return "A tiempo";
        }
        LocalTime now = LocalTime.now();
        LocalTime start = t.getStartTime();
        LocalTime end = t.getEndTime();
        if (now.isAfter(end)) {
            return "Finalizada";
        }
        if (!now.isBefore(start)) {
            return "En curso";
        }
        long minutes = Duration.between(now, start).toMinutes();
        if (minutes <= 15) {
            return "Pronto";
        }
        return "A tiempo";
    }

    /** CSS class for the talk state badge. */
    public static String talkStateClass(Talk t) {
        return switch (talkState(t)) {
            case "Pronto" -> "warning";
            case "En curso" -> "info";
            case "Finalizada" -> "past";
            default -> "success";
        };
    }
}
