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

    @Inject
    com.scanales.eventflow.service.UserProfileService userProfileService;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance quests(List<Quest> quests, String filter);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list(@jakarta.ws.rs.QueryParam("filter") String filter) {
        List<Quest> quests = questService.getQuestBoard();

        if ("mine".equalsIgnoreCase(filter)) {
            String userId = currentUserId();
            if (userId != null) {
                var profile = userProfileService.find(userId);
                if (profile.isPresent() && profile.get().getGithub() != null) {
                    String login = profile.get().getGithub().login();
                    quests = quests.stream()
                            .filter(q -> q.assignees() != null && q.assignees().stream()
                                    .anyMatch(a -> a.equalsIgnoreCase(login)))
                            .toList();
                } else {
                    // No github linked, so no assigned quests
                    quests = java.util.Collections.emptyList();
                }
            }
        }

        return withLayoutData(Templates.quests(quests, filter), "quests");
    }

    private String currentUserId() {
        if (identity == null || identity.isAnonymous()) {
            return null;
        }
        // Try email first as per convention in other resources
        String email = null;
        if (identity.getPrincipal() instanceof io.quarkus.oidc.runtime.OidcJwtCallerPrincipal jwt) {
            email = jwt.getClaim("email");
        }
        if (email != null && !email.isBlank()) {
            return email.toLowerCase();
        }
        return identity.getPrincipal().getName();
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
