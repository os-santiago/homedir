package com.scanales.eventflow.private_;

import com.scanales.eventflow.model.Quest;
import com.scanales.eventflow.service.QuestService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/quests")
@Authenticated
public class QuestBoardResource {

    @Inject
    QuestService questService;

    @Inject
    io.quarkus.security.identity.SecurityIdentity identity;

    @Inject
    com.scanales.eventflow.service.UserSessionService userSessionService;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance quests(List<Quest> quests);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        List<Quest> quests = questService.getQuestBoard();
        return withLayoutData(Templates.quests(quests), "quests");
    }

    private TemplateInstance withLayoutData(TemplateInstance templateInstance, String activePage) {
        boolean authenticated = identity != null && !identity.isAnonymous();
        String userName = authenticated ? identity.getPrincipal().getName() : null;
        return templateInstance
                .data("activePage", activePage)
                .data("userAuthenticated", authenticated)
                .data("userName", userName)
                .data("userSession", userSessionService.getCurrentSession())
                .data("userInitial", initialFrom(userName));
    }

    private String initialFrom(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.substring(0, 1).toUpperCase();
    }
}
