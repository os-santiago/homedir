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
            } else {
                // Not logged in, can't see "mine"
                // Redirect to login or show empty?
                // For now return empty
                quests = java.util.Collections.emptyList();
            }
        }

        return withLayoutData(Templates.quests(quests, filter), "quests");
    }

    @jakarta.ws.rs.POST
    @Path("/{id}/complete")
    @Authenticated
    public jakarta.ws.rs.core.Response complete(@jakarta.ws.rs.PathParam("id") String id) {
        String userId = currentUserId();
        if (userId == null) {
            return jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.UNAUTHORIZED).build();
        }

        // Validate quest exists (simple lookup for now)
        List<Quest> quests = questService.getQuestBoard();
        Quest quest = quests.stream().filter(q -> q.id().equals(id)).findFirst().orElse(null);

        if (quest != null) {
            userProfileService.addXp(userId, quest.xpReward(), "Completada Misión: " + quest.title());
        }

        return jakarta.ws.rs.core.Response.seeOther(java.net.URI.create("/quests")).build();
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
