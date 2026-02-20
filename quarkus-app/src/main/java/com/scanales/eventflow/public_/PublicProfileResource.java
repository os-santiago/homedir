package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.CommunityMember;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.QuestClass;
import com.scanales.eventflow.model.QuestProfile;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.CommunityService;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.QuestService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.qute.Template;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumMap;
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

    @Inject
    UsageMetricsService metrics;

    @Inject
    GamificationService gamificationService;

    @Inject
    SecurityIdentity identity;

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
        metrics.recordFunnelStep("profile.public.open");
        currentUserId()
            .ifPresent(
                viewer -> gamificationService.award(
                    viewer, GamificationActivity.PUBLIC_PROFILE_VIEW, resolved.canonicalUsername()));

        QuestProfile questProfile = questService.getProfile(resolved.userId());
        UserProfile profile = userProfileService.find(resolved.userId()).orElse(null);
        QuestClass dominantClass = profile != null ? profile.getDominantQuestClass() : null;
        List<PublicClassProgress> classProgress = buildClassProgress(profile, questProfile.currentXp);

        String questClassEmoji = dominantClass != null ? dominantClass.getEmoji() : "🌱";
        String questClassLabel = dominantClass != null ? dominantClass.getDisplayName() : "Novice";
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
            .data("questClass", questClassLabel)
            .data("questClassEmoji", questClassEmoji)
            .data("classProgress", classProgress)
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

    private List<PublicClassProgress> buildClassProgress(UserProfile profile, int fallbackXp) {
        EnumMap<QuestClass, Integer> xpMap = new EnumMap<>(QuestClass.class);
        if (profile != null && profile.getClassXp() != null) {
            xpMap.putAll(profile.getClassXp());
        }
        int total = xpMap.values().stream().mapToInt(v -> Math.max(0, v)).sum();
        if (total <= 0 && profile != null && fallbackXp > 0) {
            QuestClass legacyClass =
                profile.getDominantQuestClass() != null ? profile.getDominantQuestClass() : QuestClass.ENGINEER;
            xpMap.put(legacyClass, fallbackXp);
            total = fallbackXp;
        }
        final int totalXp = total;
        return java.util.Arrays.stream(QuestClass.values())
            .map(
                qc -> {
                    int xp = Math.max(0, xpMap.getOrDefault(qc, 0));
                    int percent = totalXp <= 0 ? 0 : (int) Math.round((xp * 100.0d) / totalXp);
                    return new PublicClassProgress(
                        qc.getDisplayName(),
                        qc.getEmoji(),
                        xp,
                        questService.calculateLevel(xp),
                        percent);
                })
            .toList();
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

    private Optional<String> currentUserId() {
        if (identity == null || identity.isAnonymous()) {
            return Optional.empty();
        }
        String email = AdminUtils.getClaim(identity, "email");
        if (email != null && !email.isBlank()) {
            return Optional.of(email.toLowerCase(Locale.ROOT));
        }
        String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
        if (principal == null || principal.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(principal.toLowerCase(Locale.ROOT));
    }

    private record PublicClassProgress(String className, String emoji, int xp, int level, int percent) {
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
