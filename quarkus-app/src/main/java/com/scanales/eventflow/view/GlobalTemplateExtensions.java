package com.scanales.eventflow.view;

import com.scanales.eventflow.model.UserSession;
import com.scanales.eventflow.service.UserSessionService;
import com.scanales.eventflow.model.CharacterProfile;
import com.scanales.eventflow.service.CharacterService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.TemplateGlobal;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.LaunchMode;
import jakarta.inject.Inject;

@ApplicationScoped
@Unremovable
public class GlobalTemplateExtensions {

    @TemplateGlobal(name = "appVersion")
    public static String appVersion() {
        try {
            return org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getValue("quarkus.application.version", String.class);
        } catch (Exception e) {
            return "dev";
        }
    }

    /**
     * Exposes the current user session to all templates as {userSession}.
     */
    @TemplateGlobal(name = "userSession")
    public static UserSession userSession() {
        try {
            UserSessionService service = Arc.container().instance(UserSessionService.class).get();
            if (service != null) {
                return service.getCurrentSession();
            }
        } catch (Exception e) {
            return UserSession.anonymous();
        }
        return UserSession.anonymous();
    }

    /**
     * Exposes the current character profile to all templates as {character}.
     */
    @TemplateGlobal(name = "character")
    public static CharacterProfile character() {
        try {
            UserSessionService sessionService = Arc.container().instance(UserSessionService.class).get();
            CharacterService charService = Arc.container().instance(CharacterService.class).get();

            if (sessionService != null && charService != null) {
                var session = sessionService.getCurrentSession();
                if (session.loggedIn()) {
                    return charService.getCharacter(session.email());
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return CharacterProfile.visitor();
    }

    @TemplateGlobal(name = "system")
    public static SystemStatus system() {
        try {
            com.scanales.eventflow.service.PersistenceService persistence = Arc.container()
                    .instance(com.scanales.eventflow.service.PersistenceService.class).get();
            if (persistence != null) {
                boolean lowDisk = persistence.isLowDiskSpace();
                // We could also check queue depth, but low disk is the critical one for now
                return new SystemStatus(lowDisk, lowDisk ? "Modo degradado: Almacenamiento crítico." : "Normal");
            }
        } catch (Exception e) {
            // ignore
        }
        return new SystemStatus(false, "Unknown");
    }

    @TemplateGlobal(name = "i18n")
    public static com.scanales.eventflow.config.AppMessages i18n() {
        try {
            return Arc.container().instance(com.scanales.eventflow.config.AppMessages.class).get();
        } catch (Exception e) {
            return null;
        }
    }

    @TemplateGlobal(name = "isDev")
    public static boolean isDev() {
        return LaunchMode.current() == LaunchMode.DEVELOPMENT;
    }

    @io.quarkus.runtime.annotations.RegisterForReflection
    public record SystemStatus(boolean degraded, String message) {
    }
}
