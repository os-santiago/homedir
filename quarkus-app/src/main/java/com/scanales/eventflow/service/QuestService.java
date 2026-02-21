package com.scanales.eventflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.scanales.eventflow.model.Quest;
import com.scanales.eventflow.model.QuestProfile;
import com.scanales.eventflow.model.UserProfile;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Startup
@ApplicationScoped
@io.quarkus.runtime.annotations.RegisterForReflection(targets = { QuestService.QuestsYaml.class, Quest.class })
public class QuestService {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Progression curve goals:
     * - L1 -> L1000 ~ 100k XP (~5 years with ~55 XP/day average).
     * - Technical cap remains 9999.
     */
    static final int MAX_LEVEL = 9999;
    static final int TARGET_LEVEL = 1000;
    static final int TARGET_LEVEL_XP = 100_000;
    static final double BASE_XP_PER_LEVEL = 100.0d;
    static final double CURVE_A = computeCurveA();

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quests.github.repo-owner", defaultValue = "os-santiago")
    String repoOwner;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quests.github.repo-name", defaultValue = "community-directory")
    String repoName;

    @Inject
    public QuestService() {
        Log.infof(
                "Gamification progression loaded with dynamic curve (maxLevel=%d, targetLevel=%d, targetXp=%d).",
                MAX_LEVEL,
                TARGET_LEVEL,
                TARGET_LEVEL_XP);
    }

    @Inject
    UserProfileService userProfileService;

    public QuestProfile getProfile(String username) {
        return getProfile(username, null);
    }

    public QuestProfile getProfile(String username, Integer historyLimit) {
        int currentXp = 0;
        List<QuestProfile.QuestHistoryItem> history = new ArrayList<>();

        if (username != null) {
            UserProfile profile = userProfileService.find(username).orElse(null);
            if (profile != null) {
                currentXp = profile.getCurrentXp();
                List<UserProfile.QuestHistoryItem> storedHistory = profile.getHistory();
                if (storedHistory != null && !storedHistory.isEmpty()) {
                    int size = storedHistory.size();
                    int from = 0;
                    if (historyLimit != null && historyLimit > 0) {
                        from = Math.max(0, size - historyLimit);
                    }
                    for (int i = size - 1; i >= from; i--) {
                        UserProfile.QuestHistoryItem item = storedHistory.get(i);
                        history.add(new QuestProfile.QuestHistoryItem(item.title(), item.xp(), item.date()));
                    }
                }
            } else {
                // Return empty profile for unknown user, or could create one
                // For read-only view, 0 is fine.
            }
        }

        int currentLevel = calculateLevel(currentXp);
        int nextLevelXp = currentLevel >= MAX_LEVEL ? getXpForLevel(MAX_LEVEL) : getXpForLevel(currentLevel + 1);

        return new QuestProfile(username, currentLevel, currentXp, nextLevelXp, history);
    }

    public int calculateLevel(int xp) {
        if (xp <= 0) {
            return 1;
        }
        int low = 1;
        int high = MAX_LEVEL;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (xp >= xpForLevel(mid)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    public int getXpForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        if (level > MAX_LEVEL) {
            return xpForLevel(MAX_LEVEL) + 1000;
        }
        return xpForLevel(level);
    }

    public List<Quest> getQuestBoard() {
        if (quests == null || quests.isEmpty()) {
            loadQuests();
        }
        return quests != null ? quests : Collections.emptyList();
    }

    private List<Quest> quests = new ArrayList<>();

    @Inject
    public void init() {
        loadQuests();
    }

    private void loadQuests() {
        // 1. Try to load from Remote URL (Primary Source)
        String questsUrl = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                .getOptionalValue("quests.content.url", String.class)
                .orElse(null);

        if (questsUrl != null && !questsUrl.isBlank()) {
            try (java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient()) {
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(questsUrl))
                        .GET()
                        .build();

                Log.info("Fetching quests from: " + questsUrl);

                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    QuestsYaml data = yamlMapper.readValue(response.body(), QuestsYaml.class);
                    if (data != null && data.quests != null) {
                        this.quests = data.quests;
                        Log.info("Loaded " + quests.size() + " quests from remote URL.");
                        return; // Success, skip fallback
                    }
                } else {
                    Log.error("Failed to fetch quests from URL. Status: " + response.statusCode());
                }
            } catch (Exception e) {
                Log.error("Error fetching remote quests", e);
            }
        }

        // 2. Fallback: Local Resource (Only if remote fails or not configured)
        Log.warn("Falling back to local initial-quests.yaml");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("quests/initial-quests.yaml")) {
            if (is != null) {
                QuestsYaml data = yamlMapper.readValue(is, QuestsYaml.class);
                if (data != null && data.quests != null) {
                    this.quests = data.quests;
                    Log.info("Loaded " + quests.size() + " quests from local YAML (Fallback).");
                }
            } else {
                Log.warn("initial-quests.yaml not found in classpath.");
            }
        } catch (Exception e) {
            Log.error("Failed to load local quests", e);
        }
    }

    public static class QuestsYaml {
        public List<Quest> quests;
    }

    static int xpForLevel(int level) {
        int normalized = Math.max(1, Math.min(level, MAX_LEVEL)) - 1;
        double value = (BASE_XP_PER_LEVEL * normalized) + (CURVE_A * normalized * normalized);
        return (int) Math.round(Math.max(0d, value));
    }

    private static double computeCurveA() {
        int n = TARGET_LEVEL - 1;
        if (n <= 0) {
            return 0d;
        }
        double numerator = TARGET_LEVEL_XP - (BASE_XP_PER_LEVEL * n);
        double denominator = (double) n * n;
        double value = numerator / denominator;
        return Math.max(0d, value);
    }

    public void startQuest(String userId, String questId, String githubToken) {
        var profileOpt = userProfileService.find(userId);
        if (profileOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        var profile = profileOpt.get();

        // 1. Check if already active
        if (profile.getActiveQuests() != null && profile.getActiveQuests().contains(questId)) {
            Log.info("Quest " + questId + " already active for user " + userId);
            return;
        }

        // 2. Add to active quests
        if (profile.getActiveQuests() == null) {
            profile.setActiveQuests(new ArrayList<>());
        }
        profile.getActiveQuests().add(questId);
        userProfileService.update(profile);
        Log.info("User " + userId + " started quest " + questId);

        // 3. Automation (Specific to Zero to Hero)
        if ("q-001".equals(questId)) {
            forkRepository(githubToken);
        }
    }

    private void forkRepository(String token) {
        if (token == null || token.isBlank()) {
            Log.warn("No GitHub token available for forking.");
            return;
        }

        try (java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient()) {
            String forkUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/forks";

            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(forkUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();

            client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 202) {
                            Log.info("Fork triggered successfully.");
                        } else {
                            Log.error("Failed to trigger fork. Status: " + response.statusCode() + " Body: "
                                    + response.body());
                        }
                    });
        } catch (Exception e) {
            Log.error("Error triggering fork automation", e);
        }
    }

    public void completeQuest(String userId, String questId) {
        var profileOpt = userProfileService.find(userId);
        if (profileOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        var profile = profileOpt.get();

        // Find Quest Definition
        Quest quest = getQuestBoard().stream()
                .filter(q -> q.id().equals(questId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Quest not found"));

        // Validate Quest is Active (Must be started first)
        if (profile.getActiveQuests() == null || !profile.getActiveQuests().contains(questId)) {
            Log.warn("User " + userId + " tried to complete quest " + questId + " without starting it first.");
            throw new IllegalArgumentException("Quest must be started before completion");
        }

        // Validate History (Deduplication)
        if (!quest.repeatable()) {
            boolean alreadyCompleted = false;
            if (profile.getHistory() != null) {
                // Check by title since history items store title, not ID directly (historical
                // design)
                // Using "Completada Misión: " prefix convention or just title match
                String expectedTitle = "Completada Misión: " + quest.title();
                alreadyCompleted = profile.getHistory().stream()
                        .anyMatch(item -> item.title().equals(expectedTitle));
            }

            if (alreadyCompleted) {
                Log.warn("User " + userId + " tried to complete non-repeatable quest " + questId + " again.");
                return; // Idempotent success (don't throw error to avoid UI crash, just don't award XP)
            }
        }

        // Award XP
        userProfileService.addXp(userId, quest.xpReward(), "Completada Misión: " + quest.title());

        // Remove from active list if present
        if (profile.getActiveQuests() != null && profile.getActiveQuests().contains(questId)) {
            profile.getActiveQuests().remove(questId);
            userProfileService.update(profile);
        }
    }

    public void fixQuestHistory(String userId) {
        var profileOpt = userProfileService.find(userId);
        if (profileOpt.isEmpty()) {
            return;
        }
        var profile = profileOpt.get();
        if (profile.getHistory() == null || profile.getHistory().isEmpty()) {
            return;
        }

        List<UserProfile.QuestHistoryItem> originalHistory = new ArrayList<>(profile.getHistory());
        List<UserProfile.QuestHistoryItem> distinctHistory = new ArrayList<>();
        java.util.Set<String> seenTitles = new java.util.HashSet<>();

        // Reconstruct history (Keep Oldest or Newest? Usually keep Oldest for "First
        // time completed")
        // But since list is likely appended, preserving Order is good.
        // We want to keep the FIRST occurrence of a "Completada Misión: X"

        int recalculatedXp = 0;

        for (UserProfile.QuestHistoryItem item : originalHistory) {
            if (item == null || item.title() == null) {
                continue; // Skip corrupt data
            }

            if (item.title().startsWith("Completada Misión: ")) {
                if (!seenTitles.contains(item.title())) {
                    seenTitles.add(item.title());
                    distinctHistory.add(item);
                    recalculatedXp += item.xp();
                } else {
                    Log.info("Removing duplicate history entry: " + item.title());
                }
            } else {
                // Non-quest history (e.g. "Participación Evento"), keep it
                distinctHistory.add(item);
                recalculatedXp += item.xp();
            }
        }

        // Apply changes
        if (distinctHistory.size() < originalHistory.size()) {
            Log.info("Fixing history for user " + userId + ". Removed "
                    + (originalHistory.size() - distinctHistory.size()) + " duplicates.");
            profile.setHistory(distinctHistory);

            // Fix XP total
            // Current XP might be higher than sum of history if initial XP > 0 or external
            // sources
            // But usually CurrentXP = Sum(History). Let's trust the recalc for safety
            // against inflation.
            profile.setCurrentXp(recalculatedXp);

            userProfileService.update(profile);
        }
    }
}
