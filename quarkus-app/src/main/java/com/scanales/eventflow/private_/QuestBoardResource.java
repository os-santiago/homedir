package com.scanales.eventflow.private_;

import com.scanales.eventflow.model.Quest;
import com.scanales.eventflow.model.UserSession;
import com.scanales.eventflow.service.QuestService;
import com.scanales.eventflow.util.TemplateLocaleUtil;
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

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {
        public static native TemplateInstance quests(List<Quest> quests, String filter, UserSession userSession);

    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list(
            @jakarta.ws.rs.QueryParam("filter") String filter,
            @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie) {
        List<Quest> quests = questService.getQuestBoard();

        if ("mine".equalsIgnoreCase(filter)) {
            String userId = currentUserId();
            if (userId != null) {
                var profileOpt = userProfileService.find(userId);
                if (profileOpt.isPresent()) {
                    var profile = profileOpt.get();
                    var activeQuests = profile.getActiveQuests() != null ? profile.getActiveQuests()
                            : List.<String>of();
                    String login = profile.getGithub() != null ? profile.getGithub().login() : null;

                    quests = quests.stream()
                            .filter(q -> (activeQuests.contains(q.id())) || // Locally active
                                    (login != null && q.assignees() != null && q.assignees().stream()
                                            .anyMatch(a -> a.equalsIgnoreCase(login))) // GitHub active
                            )
                            .toList();
                } else {
                    quests = java.util.Collections.emptyList();
                }
            } else {
                quests = java.util.Collections.emptyList();
            }
        }

        UserSession session = userSessionService.getCurrentSession();
        return withLayoutData(Templates.quests(quests, filter, session), "quests", localeCookie);
    }

    @jakarta.ws.rs.POST
    @Path("/{id}/start")
    @Authenticated
    public jakarta.ws.rs.core.Response start(@jakarta.ws.rs.PathParam("id") String id) {
        String userId = currentUserId();
        if (userId == null) {
            return jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.UNAUTHORIZED).build();
        }

        // Get GitHub token if available (from OIDC context)
        String token = null;
        if (identity.getPrincipal() instanceof io.quarkus.oidc.runtime.OidcJwtCallerPrincipal jwt) {
            // Check specific claim or access token
            // Note: For Google auth this won't be a GitHub token.
            // We need to handle this carefully.
            // Ideally we used the stored access token if we did the GitHub Flow
            // differently.
            // For now, let's try to get it if available or null.
            // token = jwt.getRawToken(); // This is the ID Token usually.
        }

        // TODO: Pass actual GitHub token if we can get it from OIDC UserInfo or stored
        // creds.
        // For MVP 3.6.2, we passed null unless we have a clear way to get it.
        // If we use GitHub Login, the access token might be accessible via
        // `identity.getCredential(AccessTokenCredential.class)`

        questService.startQuest(userId, id, token);

        return jakarta.ws.rs.core.Response.seeOther(java.net.URI.create("/quests?filter=mine")).build();
    }

    @jakarta.ws.rs.POST
    @Path("/{id}/complete")
    @Authenticated
    public jakarta.ws.rs.core.Response complete(@jakarta.ws.rs.PathParam("id") String id) {
        String userId = currentUserId();
        if (userId == null) {
            return jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.UNAUTHORIZED).build();
        }

        try {
            questService.completeQuest(userId, id);
        } catch (IllegalArgumentException e) {
            // Log.warn("Invalid quest completion attempt: " + e.getMessage());
        }

        return jakarta.ws.rs.core.Response.seeOther(java.net.URI.create("/quests")).build();
    }

    @jakarta.ws.rs.POST
    @Path("/fix-history")
    @Authenticated
    public jakarta.ws.rs.core.Response fixHistory() {
        String userId = currentUserId();
        if (userId == null) {
            return jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.UNAUTHORIZED).build();
        }

        questService.fixQuestHistory(userId);

        // Redirect back to profile or quests
        return jakarta.ws.rs.core.Response.seeOther(java.net.URI.create("/private/profile")).build();
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

    private TemplateInstance withLayoutData(
            TemplateInstance templateInstance, String activePage, String localeCookie) {
        boolean authenticated = identity != null && !identity.isAnonymous();
        String userName = authenticated ? identity.getPrincipal().getName() : null;
        return TemplateLocaleUtil.apply(templateInstance, localeCookie)
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
