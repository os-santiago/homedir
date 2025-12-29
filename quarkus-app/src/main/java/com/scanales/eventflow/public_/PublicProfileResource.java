package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.CommunityMember;
import com.scanales.eventflow.model.QuestProfile;
import com.scanales.eventflow.service.CommunityService;
import com.scanales.eventflow.service.QuestService;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;

@Path("/u")
public class PublicProfileResource {

    @Inject
    Template publicProfile; // maps to public-profile.qute.html

    @Inject
    CommunityService communityService;

    @Inject
    QuestService questService;

    @Inject
    UserProfileService userProfileService;

    @GET
    @Path("/{username}")
    @Produces(MediaType.TEXT_HTML)
    public Response getPublicProfile(@PathParam("username") String username) {
        // 1. Resolve Github handle to User ID (Email)
        String userId = null;
        Optional<CommunityMember> memberOpt = communityService.findByGithub(username);

        if (memberOpt.isPresent()) {
            userId = memberOpt.get().getUserId();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // 2. Fetch Quest Data using UserID
        QuestProfile questProfile = questService.getProfile(userId);

        String questClassEmoji = "🌱"; // Placeholder
        int questsCompleted = questProfile.history != null ? questProfile.history.size() : 0;
        long xpPercentage = 0;
        if (questProfile.nextLevelXp > 0) {
            xpPercentage = Math.round(((double) questProfile.currentXp / (double) questProfile.nextLevelXp) * 100);
        }

        // 3. Render Template
        return Response.ok(publicProfile
                .data("pageTitle", memberOpt.get().getDisplayName() + " (@" + username + ")")
                .data("username", username)
                .data("displayName", memberOpt.get().getDisplayName())
                .data("avatarUrl", memberOpt.get().getAvatarUrl())
                .data("level", questProfile.level)
                .data("currentXp", questProfile.currentXp)
                .data("nextLevelXp", questProfile.nextLevelXp)
                .data("xpPercentage", xpPercentage)
                .data("questClass", "Novice")
                .data("questClassEmoji", questClassEmoji)
                .data("questsCompleted", questsCompleted)
                .data("badges", memberOpt.map(CommunityMember::getBadges).orElse(java.util.List.of()))
                .data("history",
                        questProfile.history != null ? questProfile.history.stream().limit(5).toList()
                                : java.util.List.of())
                .data("ogTitle", memberOpt.get().getDisplayName() + " - Homedir Profile")
                .data("ogDescription", "Check out " + username + "'s developer stats and quests.")
                .data("ogImage", "https://og.scanales.com/api/og?title=" + username + "&subtitle=Level "
                        + questProfile.level))
                .build();
    }
}
