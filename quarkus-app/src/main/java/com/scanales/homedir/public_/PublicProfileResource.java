package com.scanales.homedir.public_;

import com.scanales.homedir.challenges.ChallengeService;
import com.scanales.homedir.cfp.CfpSubmission;
import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.cfp.CfpSubmissionStatus;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.QuestClass;
import com.scanales.homedir.model.QuestProfile;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.config.AppMessages;
import com.scanales.homedir.model.CommunityMember;
import com.scanales.homedir.service.CommunityService;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.QuestService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.reputation.ReputationProfileSummaryService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
import com.scanales.homedir.volunteers.VolunteerApplication;
import com.scanales.homedir.volunteers.VolunteerApplicationService;
import com.scanales.homedir.volunteers.VolunteerApplicationStatus;
import com.scanales.homedir.economy.EconomyInventoryItem;
import com.scanales.homedir.economy.EconomyService;
import io.quarkus.qute.Template;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

@Path("/u")
public class PublicProfileResource {
    private static final int DOMINANT_HYBRID_MARGIN_XP = 5;
    private static final int DOMINANT_HYBRID_MARGIN_PERCENT = 12;
    private static final DateTimeFormatter SHARE_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    @Inject
    Template publicProfile;

    @Inject
    Template publicChallenge;

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

    @Inject
    AppMessages messages;

    @Inject
    CfpSubmissionService cfpSubmissionService;

    @Inject
    EventService eventService;
    @Inject
    VolunteerApplicationService volunteerApplicationService;

    @Inject
    EconomyService economyService;

    @Inject
    ChallengeService challengeService;
    @Inject
    ReputationProfileSummaryService reputationProfileSummaryService;

    @GET
    @Path("/{username}")
    @Produces(MediaType.TEXT_HTML)
    public Response getPublicProfile(
        @PathParam("username") String username,
        @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
        @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers) {
        String requested = normalizeId(username);
        if (requested == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<ResolvedPublicProfile> resolvedOpt = resolveProfile(requested);
        if (resolvedOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String resolvedLocaleCode = TemplateLocaleUtil.resolve(localeCookie, headers);
        ResolvedPublicProfile resolved = resolvedOpt.get();
        metrics.recordFunnelStep("profile.public.open");
        currentUserId()
            .ifPresent(
                viewer -> gamificationService.award(
                    viewer, GamificationActivity.PUBLIC_PROFILE_VIEW, resolved.canonicalUsername()));

        QuestProfile questProfile = questService.getProfile(resolved.userId(), 5);
        UserProfile profile = userProfileService.find(resolved.userId()).orElse(null);
        UserProfile.SpeakerProfile speakerProfile = profile != null ? profile.getSpeakerProfile() : null;
        boolean speakerActive = speakerProfile != null && speakerProfile.active();
        List<PublicClassProgress> classProgress = buildClassProgress(profile, questProfile.currentXp);
        DominantClassSummary dominantClassSummary = resolveDominantClassSummary(classProgress);
        QuestClass dominantClass = dominantClassSummary.questClass();
        String dominantClassMessage = dominantClassSummary.message();

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
        java.util.Set<String> cfpUserIds = resolveCfpUserIds(profile, resolved.userId());
        boolean hasProfileGlow = hasInventoryItem(cfpUserIds, "profile-glow");
        List<PublicChallengeItem> completedChallenges =
            buildPublicChallenges(cfpUserIds, resolved.canonicalUsername(), resolvedLocaleCode);
        ReputationProfileSummaryService.PublicProfileSummary reputationSummary =
            reputationProfileSummaryService.summaryForUser(resolved.userId()).orElse(null);
        CfpSubmissionService.MineStats cfpStats = cfpSubmissionService.visibleStatsMineAcrossEvents(cfpUserIds);
        int cfpAcceptedCount = cfpStats.countsByStatus().getOrDefault(CfpSubmissionStatus.ACCEPTED, 0);
        List<PublicCfpItem> cfpRecentAccepted =
            cfpSubmissionService
                .listMineAcrossEvents(cfpUserIds, CfpSubmissionService.SortOrder.UPDATED_DESC, 12, 0)
                .stream()
                .filter(item -> cfpSubmissionService.visibleStatus(item) == CfpSubmissionStatus.ACCEPTED)
                .limit(3)
                .map(this::toPublicCfpItem)
                .toList();
        VolunteerApplicationService.MineStats volunteerStats =
            volunteerApplicationService.statsMineAcrossEvents(cfpUserIds);
        int volunteerSelectedCount =
            volunteerStats.countsByStatus().getOrDefault(VolunteerApplicationStatus.SELECTED, 0);
        List<PublicVolunteerItem> volunteerRecentSelected =
            volunteerApplicationService
                .listMineAcrossEvents(cfpUserIds, VolunteerApplicationService.SortOrder.UPDATED_DESC, 12, 0)
                .stream()
                .filter(item -> item.status() == VolunteerApplicationStatus.SELECTED)
                .limit(3)
                .map(this::toPublicVolunteerItem)
                .toList();

        return Response.ok(TemplateLocaleUtil.apply(
            publicProfile
                .data("pageTitle", resolved.displayName() + " (@" + resolved.canonicalUsername() + ")")
                .data("username", resolved.canonicalUsername())
                .data("localeCode", resolvedLocaleCode)
                .data("displayName", resolved.displayName())
                .data("avatarUrl", resolved.avatarUrl())
                .data("level", questProfile.level)
                .data("currentXp", questProfile.currentXp)
                .data("nextLevelXp", questProfile.nextLevelXp)
                .data("xpPercentage", xpPercentage)
                .data("questClass", questClassLabel)
                .data("questClassEmoji", questClassEmoji)
                .data("dominantClassMessage", dominantClassMessage)
                .data("classProgress", classProgress)
                .data("questsCompleted", questsCompleted)
                .data("badges", badges)
                .data("history", questProfile.history != null ? questProfile.history : List.of())
                .data("githubLogin", resolved.githubLogin())
                .data("githubProfileUrl", resolved.githubProfileUrl())
                .data("discordHandle", resolved.discordHandle())
                .data("discordProfileUrl", resolved.discordProfileUrl())
                .data("homedirId", resolved.homedirId())
                .data("hasGithub", hasGithub)
                .data("hasDiscord", hasDiscord)
                .data("hasLinkedAccounts", hasGithub || hasDiscord)
                .data("hasProfileGlow", hasProfileGlow)
                .data("cfpAcceptedCount", cfpAcceptedCount)
                .data("cfpRecentAccepted", cfpRecentAccepted)
                .data("speakerProfile", speakerProfile)
                .data("speakerActive", speakerActive)
                .data("volunteerSelectedCount", volunteerSelectedCount)
                .data("volunteerRecentSelected", volunteerRecentSelected)
                .data("completedChallenges", completedChallenges)
                .data("reputationSummary", reputationSummary)
                .data("ogTitle", resolved.displayName() + " - Homedir Profile")
                .data("ogDescription", "Check out @" + resolved.canonicalUsername() + " and their community activity.")
                .data(
                    "ogImage",
                    "https://og.scanales.com/api/og?title="
                        + resolved.canonicalUsername()
                        + "&subtitle=Level "
                        + questProfile.level),
            localeCookie,
            headers))
            .build();
    }

    @GET
    @Path("/{username}/challenges/{challengeId}")
    @Produces(MediaType.TEXT_HTML)
    public Response getPublicChallenge(
        @PathParam("username") String username,
        @PathParam("challengeId") String challengeId,
        @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie) {
        String requested = normalizeId(username);
        String requestedChallengeId = normalizeId(challengeId);
        if (requested == null || requestedChallengeId == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<ResolvedPublicProfile> resolvedOpt = resolveProfile(requested);
        if (resolvedOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String resolvedLocaleCode = TemplateLocaleUtil.resolve(localeCookie, null);
        ResolvedPublicProfile resolved = resolvedOpt.get();
        List<PublicChallengeItem> completedChallenges =
            buildPublicChallenges(resolveCfpUserIds(userProfileService.find(resolved.userId()).orElse(null), resolved.userId()),
                resolved.canonicalUsername(),
                resolvedLocaleCode);
        PublicChallengeItem challenge =
            completedChallenges.stream()
                .filter(item -> requestedChallengeId.equals(item.id()))
                .findFirst()
                .orElse(null);
        if (challenge == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        metrics.recordFunnelStep("challenge.public.open");
        currentUserId()
            .ifPresent(
                viewer -> gamificationService.award(
                    viewer, GamificationActivity.PUBLIC_PROFILE_VIEW, resolved.canonicalUsername() + ":" + challenge.id()));

        return Response.ok(TemplateLocaleUtil.apply(
            publicChallenge
                .data("pageTitle", challenge.title() + " · " + resolved.displayName())
                .data("profilePath", "/u/" + resolved.canonicalUsername())
                .data("username", resolved.canonicalUsername())
                .data("displayName", resolved.displayName())
                .data("avatarUrl", resolved.avatarUrl())
                .data("questClass", challenge.className())
                .data("questClassEmoji", challenge.classEmoji())
                .data("challenge", challenge)
                .data("ogTitle", challenge.title() + " · " + resolved.displayName())
                .data("ogDescription", challenge.shareDescription())
                .data(
                    "ogImage",
                    "https://og.scanales.com/api/og?title="
                        + encodeUrlPart(challenge.title())
                        + "&subtitle="
                        + encodeUrlPart(resolved.displayName() + " · " + challenge.rewardLabel())),
            localeCookie)).build();
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
                        qc.name(),
                        qc.getDisplayName(),
                        qc.getEmoji(),
                        xp,
                        questService.calculateLevel(xp),
                        percent);
                })
            .toList();
    }

    private DominantClassSummary resolveDominantClassSummary(List<PublicClassProgress> classProgress) {
        if (classProgress == null || classProgress.isEmpty()) {
            return new DominantClassSummary(null, null);
        }
        List<PublicClassProgress> sorted =
            classProgress.stream()
                .sorted(
                    java.util.Comparator.comparingInt(PublicClassProgress::xp)
                        .reversed()
                        .thenComparing(PublicClassProgress::value))
                .toList();
        PublicClassProgress primary = sorted.get(0);
        if (primary.xp() <= 0) {
            return new DominantClassSummary(null, null);
        }
        QuestClass primaryClass = QuestClass.fromValue(primary.value());
        if (sorted.size() > 1) {
            PublicClassProgress secondary = sorted.get(1);
            if (secondary.xp() > 0) {
                int diffXp = Math.max(0, primary.xp() - secondary.xp());
                int diffPercent = primary.xp() <= 0 ? 100 : (int) Math.round((diffXp * 100.0d) / primary.xp());
                boolean hybrid =
                    diffXp <= DOMINANT_HYBRID_MARGIN_XP || diffPercent <= DOMINANT_HYBRID_MARGIN_PERCENT;
                if (hybrid) {
                    return new DominantClassSummary(
                        primaryClass,
                        messages.profile_dominant_class_hybrid(primary.className(), secondary.className()));
                }
            }
        }
        return new DominantClassSummary(primaryClass, messages.profile_dominant_class(primary.className()));
    }

    private List<PublicChallengeItem> buildPublicChallenges(
        java.util.Set<String> userIds, String canonicalUsername, String localeCode) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        PublicChallengeCopy copy = publicChallengeCopy(localeCode);
        java.util.Map<String, PublicChallengeItem> byId = new java.util.LinkedHashMap<>();
        for (String userId : userIds) {
            for (ChallengeService.ChallengeProgressCard card : challengeService.listProgressForUser(userId)) {
                if (card == null || !card.completed() || card.id() == null || card.id().isBlank()) {
                    continue;
                }
                PublicChallengeItem candidate = toPublicChallengeItem(card, canonicalUsername, copy);
                PublicChallengeItem existing = byId.get(candidate.id());
                if (existing == null || isAfter(candidate.completedAt(), existing.completedAt())) {
                    byId.put(candidate.id(), candidate);
                }
            }
        }
        return byId.values().stream()
            .sorted(java.util.Comparator.comparing(PublicChallengeItem::completedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .toList();
    }

    private PublicChallengeItem toPublicChallengeItem(
        ChallengeService.ChallengeProgressCard card, String canonicalUsername, PublicChallengeCopy copy) {
        int totalSteps = Math.max(0, card.totalSteps());
        int completedSteps = Math.max(0, Math.min(card.completedSteps(), totalSteps));
        String title = challengeTitle(card.id(), copy);
        String description = challengeDescription(card.id(), copy);
        String rewardLabel = formatNamed(copy.rewardHcoinPattern(), "reward", Math.max(0, card.rewardHcoin()));
        String progressLabel =
            formatNamed(copy.progressStepsPattern(), "completed", completedSteps, "total", totalSteps);
        String sharePath = "/u/" + canonicalUsername + "/challenges/" + card.id();
        String completedOn =
            card.completedAt() == null
                ? copy.completedLabel()
                : copy.completedOnPrefix() + " " + SHARE_DATE_FORMAT.format(card.completedAt());
        return new PublicChallengeItem(
            card.id(),
            title,
            description,
            rewardLabel,
            progressLabel,
            completedOn,
            card.completedAt(),
            sharePath,
            "/u/" + canonicalUsername,
            challengeClassName(card.id()),
            challengeClassEmoji(card.id()),
            formatNamed(copy.shareDescriptionPattern(), "title", title, "reward", rewardLabel));
    }

    private PublicChallengeCopy publicChallengeCopy(String localeCode) {
        ResourceBundle bundle = localizedChallengeBundle(localeCode);
        return new PublicChallengeCopy(
            bundleText(bundle, "challenge_reward_hcoin"),
            bundleText(bundle, "challenge_progress_steps"),
            bundleText(bundle, "challenge_community_scout_title"),
            bundleText(bundle, "challenge_community_scout_desc"),
            bundleText(bundle, "challenge_event_explorer_title"),
            bundleText(bundle, "challenge_event_explorer_desc"),
            bundleText(bundle, "challenge_open_source_identity_title"),
            bundleText(bundle, "challenge_open_source_identity_desc"),
            bundleText(bundle, "public_profile_challenges_completed_on"),
            bundleText(bundle, "public_profile_challenge_share_description"));
    }

    private ResourceBundle localizedChallengeBundle(String localeCode) {
        Locale bundleLocale = "es".equalsIgnoreCase(localeCode) ? Locale.forLanguageTag("es") : Locale.ROOT;
        return ResourceBundle.getBundle("i18n", bundleLocale);
    }

    private String bundleText(ResourceBundle bundle, String key) {
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    private String formatNamed(String pattern, Object... keyValues) {
        String formatted = pattern;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            String value = String.valueOf(keyValues[i + 1]);
            formatted = formatted.replace("{" + key + "}", value);
        }
        return formatted;
    }

    private String challengeTitle(String challengeId, PublicChallengeCopy copy) {
        return switch (challengeId) {
            case "community-scout" -> copy.communityScoutTitle();
            case "event-explorer" -> copy.eventExplorerTitle();
            case "open-source-identity" -> copy.openSourceIdentityTitle();
            default -> challengeId;
        };
    }

    private String challengeDescription(String challengeId, PublicChallengeCopy copy) {
        return switch (challengeId) {
            case "community-scout" -> copy.communityScoutDesc();
            case "event-explorer" -> copy.eventExplorerDesc();
            case "open-source-identity" -> copy.openSourceIdentityDesc();
            default -> challengeId;
        };
    }

    private String challengeClassName(String challengeId) {
        QuestClass questClass = challengeClass(challengeId);
        return questClass != null ? questClass.getDisplayName() : "";
    }

    private String challengeClassEmoji(String challengeId) {
        QuestClass questClass = challengeClass(challengeId);
        return questClass != null ? questClass.getEmoji() : "🌱";
    }

    private QuestClass challengeClass(String challengeId) {
        return switch (challengeId) {
            case "community-scout" -> QuestClass.SCIENTIST;
            case "event-explorer" -> QuestClass.ENGINEER;
            case "open-source-identity" -> QuestClass.MAGE;
            default -> null;
        };
    }

    private boolean isAfter(java.time.Instant left, java.time.Instant right) {
        if (left == null) {
            return false;
        }
        if (right == null) {
            return true;
        }
        return left.isAfter(right);
    }

    private String encodeUrlPart(String value) {
        String normalized = value == null ? "" : value;
        return URLEncoder.encode(normalized, StandardCharsets.UTF_8);
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

    private java.util.Set<String> resolveCfpUserIds(UserProfile profile, String resolvedUserId) {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        addNormalizedUserId(ids, resolvedUserId);
        if (profile != null) {
            addNormalizedUserId(ids, profile.getUserId());
            addNormalizedUserId(ids, profile.getEmail());
        }
        return ids.isEmpty() ? java.util.Set.of() : java.util.Collections.unmodifiableSet(ids);
    }

    private PublicCfpItem toPublicCfpItem(CfpSubmission submission) {
        if (submission == null) {
            return new PublicCfpItem("", "", "/eventos");
        }
        String eventId = submission.eventId() != null ? submission.eventId() : "";
        com.scanales.homedir.model.Event event = eventService.getEvent(eventId);
        String eventTitle =
            event != null && event.getTitle() != null && !event.getTitle().isBlank()
                ? event.getTitle()
                : eventId;
        return new PublicCfpItem(
            submission.title() != null ? submission.title() : "",
            eventTitle,
            "/event/" + eventId);
    }

    private PublicVolunteerItem toPublicVolunteerItem(VolunteerApplication application) {
        if (application == null) {
            return new PublicVolunteerItem("", "", "");
        }
        String eventId = application.eventId() != null ? application.eventId() : "";
        com.scanales.homedir.model.Event event = eventService.getEvent(eventId);
        String eventTitle =
            event != null && event.getTitle() != null && !event.getTitle().isBlank()
                ? event.getTitle()
                : eventId;
        return new PublicVolunteerItem(eventTitle, "/event/" + eventId + "/volunteers", eventId);
    }

    private static void addNormalizedUserId(java.util.Set<String> ids, String raw) {
        if (ids == null || raw == null) {
            return;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!normalized.isBlank()) {
            ids.add(normalized);
        }
    }

    private boolean hasInventoryItem(java.util.Set<String> userIds, String itemId) {
        if (userIds == null || userIds.isEmpty() || itemId == null || itemId.isBlank()) {
            return false;
        }
        for (String userId : userIds) {
            List<EconomyInventoryItem> items = economyService.listInventory(userId, 50, 0);
            boolean match =
                items.stream()
                    .anyMatch(
                        item ->
                            item != null
                                && item.quantity() > 0
                                && itemId.equalsIgnoreCase(item.itemId()));
            if (match) {
                return true;
            }
        }
        return false;
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

    private record PublicClassProgress(
        String value, String className, String emoji, int xp, int level, int percent) {
    }

    private record DominantClassSummary(QuestClass questClass, String message) {
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

    private record PublicCfpItem(String title, String eventTitle, String eventUrl) {
    }

    private record PublicVolunteerItem(String eventTitle, String eventUrl, String eventId) {
    }

    private record PublicChallengeCopy(
        String rewardHcoinPattern,
        String progressStepsPattern,
        String communityScoutTitle,
        String communityScoutDesc,
        String eventExplorerTitle,
        String eventExplorerDesc,
        String openSourceIdentityTitle,
        String openSourceIdentityDesc,
        String completedOnPrefix,
        String shareDescriptionPattern) {
        String completedLabel() {
            return completedOnPrefix;
        }
    }

    private record PublicChallengeItem(
        String id,
        String title,
        String description,
        String rewardLabel,
        String progressLabel,
        String completedLabel,
        java.time.Instant completedAt,
        String sharePath,
        String profilePath,
        String className,
        String classEmoji,
        String shareDescription) {
    }
}
