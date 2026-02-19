package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.CommunityMember;
import com.scanales.eventflow.model.QuestProfile;
import com.scanales.eventflow.model.UserProfile;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Path("/u")
public class PublicProfileResource {

    @Inject
    Template publicProfile;

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
        String requested = normalizeId(username);
        if (requested == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<ResolvedPublicProfile> resolvedOpt = resolveProfile(requested);
        if (resolvedOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ResolvedPublicProfile resolved = resolvedOpt.get();

        QuestProfile questProfile = questService.getProfile(resolved.userId());

        String questClassEmoji = "🌱";
        int questsCompleted = questProfile.history != null ? questProfile.history.size() : 0;
        long xpPercentage = 0;
        if (questProfile.nextLevelXp > 0) {
            xpPercentage = Math.round(((double) questProfile.currentXp / (double) questProfile.nextLevelXp) * 100);
        }

        List<String> badges = resolved.badges() == null ? List.of() : resolved.badges();
        boolean hasGithub = resolved.githubLogin() != null;
        boolean hasDiscord = resolved.discordHandle() != null || resolved.discordProfileUrl() != null;

        return Response.ok(publicProfile
            .data("pageTitle", resolved.displayName() + " (@" + resolved.canonicalUsername() + ")")
            .data("username", resolved.canonicalUsername())
            .data("displayName", resolved.displayName())
            .data("avatarUrl", resolved.avatarUrl())
            .data("level", questProfile.level)
            .data("currentXp", questProfile.currentXp)
            .data("nextLevelXp", questProfile.nextLevelXp)
            .data("xpPercentage", xpPercentage)
            .data("questClass", "Novice")
            .data("questClassEmoji", questClassEmoji)
            .data("questsCompleted", questsCompleted)
            .data("badges", badges)
            .data("history",
                questProfile.history != null ? questProfile.history.stream().limit(5).toList() : List.of())
            .data("githubLogin", resolved.githubLogin())
            .data("githubProfileUrl", resolved.githubProfileUrl())
            .data("discordHandle", resolved.discordHandle())
            .data("discordProfileUrl", resolved.discordProfileUrl())
            .data("homedirId", resolved.homedirId())
            .data("hasGithub", hasGithub)
            .data("hasDiscord", hasDiscord)
            .data("hasLinkedAccounts", hasGithub || hasDiscord)
            .data("ogTitle", resolved.displayName() + " - Homedir Profile")
            .data("ogDescription", "Check out @" + resolved.canonicalUsername() + " and their community activity.")
            .data("ogImage", "https://og.scanales.com/api/og?title=" + resolved.canonicalUsername() + "&subtitle=Level "
                + questProfile.level))
            .build();
    }

    private Optional<ResolvedPublicProfile> resolveProfile(String requestedUsername) {
        Optional<ResolvedPublicProfile> direct = resolveFromUserProfiles(requestedUsername);
        if (direct.isPresent()) {
            return direct;
        }

        Optional<CommunityMember> memberOpt = communityService.findByGithub(requestedUsername);
        if (memberOpt.isEmpty()) {
            return Optional.empty();
        }
        CommunityMember member = memberOpt.get();
        String githubLogin = normalizeId(member.getGithub());
        String canonical = firstNonBlank(githubLogin, requestedUsername);
        String userId = firstNonBlank(member.getUserId(), canonical);
        String homedirId = homedirMemberId(member.getUserId(), null);
        return Optional.of(new ResolvedPublicProfile(
            canonical,
            userId,
            firstNonBlank(member.getDisplayName(), canonical),
            member.getAvatarUrl(),
            githubLogin,
            firstNonBlank(member.getProfileUrl(), githubLogin != null ? "https://github.com/" + githubLogin : null),
            null,
            null,
            homedirId,
            member.getBadges() == null ? List.of() : member.getBadges()));
    }

    private Optional<ResolvedPublicProfile> resolveFromUserProfiles(String requestedUsername) {
        for (UserProfile profile : userProfileService.allProfiles().values()) {
            if (profile == null) {
                continue;
            }
            String githubLogin = normalizeId(profile.getGithub() != null ? profile.getGithub().login() : null);
            String homedirId = homedirMemberId(firstNonBlank(profile.getUserId(), profile.getEmail()), null);
            boolean matchesGithub = githubLogin != null && githubLogin.equals(requestedUsername);
            boolean matchesHomedir = homedirId != null && homedirId.equals(requestedUsername);
            if (!matchesGithub && !matchesHomedir) {
                continue;
            }

            String canonicalUsername = firstNonBlank(githubLogin, homedirId, requestedUsername);
            String userId = firstNonBlank(profile.getUserId(), profile.getEmail(), canonicalUsername);
            String displayName = firstNonBlank(
                profile.getName(),
                usernameFromEmail(profile.getEmail()),
                githubLogin,
                canonicalUsername);
            String avatarUrl = firstNonBlank(
                profile.getGithub() != null ? profile.getGithub().avatarUrl() : null,
                profile.getDiscord() != null ? profile.getDiscord().avatarUrl() : null);
            String githubProfileUrl = firstNonBlank(
                profile.getGithub() != null ? profile.getGithub().profileUrl() : null,
                githubLogin != null ? "https://github.com/" + githubLogin : null);
            String discordId = normalizeId(profile.getDiscord() != null ? profile.getDiscord().id() : null);
            String discordProfileUrl = firstNonBlank(
                profile.getDiscord() != null ? profile.getDiscord().profileUrl() : null,
                discordId != null ? "https://discord.com/users/" + discordId : null);
            String discordHandle = firstNonBlank(
                profile.getDiscord() != null ? profile.getDiscord().handle() : null,
                discordId);
            List<String> badges = communityService.findByUserId(userId).map(CommunityMember::getBadges).orElse(List.of());
            if (badges == null) {
                badges = List.of();
            }

            return Optional.of(new ResolvedPublicProfile(
                canonicalUsername,
                userId,
                displayName,
                avatarUrl,
                githubLogin,
                githubProfileUrl,
                discordHandle,
                discordProfileUrl,
                homedirId,
                badges));
        }
        return Optional.empty();
    }

    private static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static String usernameFromEmail(String email) {
        String value = normalizeId(email);
        if (value == null) {
            return null;
        }
        int at = value.indexOf('@');
        return at > 0 ? value.substring(0, at) : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private static String homedirMemberId(String identitySeed, String githubLogin) {
        String github = normalizeId(githubLogin);
        if (github != null) {
            return "gh-" + github;
        }
        String seed = normalizeId(identitySeed);
        if (seed == null) {
            return null;
        }
        return "hd-" + shortHash(seed, 16);
    }

    private static String shortHash(String value, int maxLength) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            int end = Math.min(hex.length(), Math.max(6, maxLength));
            return hex.substring(0, end);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private record ResolvedPublicProfile(
        String canonicalUsername,
        String userId,
        String displayName,
        String avatarUrl,
        String githubLogin,
        String githubProfileUrl,
        String discordHandle,
        String discordProfileUrl,
        String homedirId,
        List<String> badges) {
    }
}
