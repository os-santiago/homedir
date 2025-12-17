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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Startup
@ApplicationScoped
@io.quarkus.runtime.annotations.RegisterForReflection(targets = { QuestService.QuestsYaml.class, Quest.class })
public class QuestService {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // Mock Data Paths
    private static final String GAMIFICATION_PATH = "/mock-data/gamification.yaml";

    // Cache for levels
    private List<LevelConfig> levelConfigs;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quests.github.repo-owner", defaultValue = "os-santiago")
    String repoOwner;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quests.github.repo-name", defaultValue = "community-directory")
    String repoName;

    @Inject
    public QuestService() {
        loadGamificationConfig();
    }

    @Inject
    UserProfileService userProfileService;

    private void loadGamificationConfig() {
        try (InputStream is = getClass().getResourceAsStream(GAMIFICATION_PATH)) {
            if (is != null) {
                String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                GamificationConfig config = yamlMapper.readValue(yaml, GamificationConfig.class);
                this.levelConfigs = config.levels;
                Log.info("Loaded gamification config with " + levelConfigs.size() + " levels.");
            } else {
                Log.error("Gamification config not found at " + GAMIFICATION_PATH);
                this.levelConfigs = Collections.emptyList();
            }
        } catch (IOException e) {
            Log.error("Failed to load gamification config", e);
            this.levelConfigs = Collections.emptyList();
        }
    }

    public QuestProfile getProfile(String username) {
        int currentXp = 0;
        List<QuestProfile.QuestHistoryItem> history = new ArrayList<>();

        if (username != null) {
            UserProfile profile = userProfileService.find(username).orElse(null);
            if (profile != null) {
                currentXp = profile.getCurrentXp();
                if (profile.getHistory() != null) {
                    for (UserProfile.QuestHistoryItem item : profile.getHistory()) {
                        history.add(new QuestProfile.QuestHistoryItem(item.title(), item.xp(), item.date()));
                    }
                }
            } else {
                // Return empty profile for unknown user, or could create one
                // For read-only view, 0 is fine.
            }
        }

        int currentLevel = calculateLevel(currentXp);
        int nextLevelXp = getXpForLevel(currentLevel + 1);

        // Reverse history to show newest first
        Collections.reverse(history);

        return new QuestProfile(username, currentLevel, currentXp, nextLevelXp, history);
    }

    public int calculateLevel(int xp) {
        int level = 1;
        for (LevelConfig config : levelConfigs) {
            if (xp >= config.xpRequired) {
                level = config.level;
            } else {
                break;
            }
        }
        return level;
    }

    public int getXpForLevel(int level) {
        return levelConfigs.stream()
                .filter(l -> l.level == level)
                .map(l -> l.xpRequired)
                .findFirst()
                .orElse(99999); // Default high if max level reached
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

    // Helper Classes for Serialization
    private static class GamificationConfig {
        public List<LevelConfig> levels;
    }

    private static class LevelConfig {
        public int level;
        public int xpRequired;
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
