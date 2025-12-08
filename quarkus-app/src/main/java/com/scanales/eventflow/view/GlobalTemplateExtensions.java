package com.scanales.eventflow.view;

import com.scanales.eventflow.model.UserSession;
import com.scanales.eventflow.service.UserSessionService;
import com.scanales.eventflow.model.CharacterProfile;
import com.scanales.eventflow.service.CharacterService;
import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateGlobal;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GlobalTemplateExtensions {

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
}
