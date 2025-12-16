package com.scanales.eventflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.scanales.eventflow.model.Quest;
import com.scanales.eventflow.model.QuestProfile;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Startup
@ApplicationScoped
public class QuestService {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // Mock Data Paths
    private static final String GAMIFICATION_PATH = "/mock-data/gamification.yaml";
    private static final String ADVENTURERS_PATH = "/mock-data/adventurers/";

    // Cache for levels
    private List<LevelConfig> levelConfigs;

    @Inject
    public QuestService() {
        loadGamificationConfig();
    }

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
        // Try to load mock data first
        try (InputStream is = getClass().getResourceAsStream(ADVENTURERS_PATH + username + ".yaml")) {
            if (is != null) {
                String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return parseProfile(yaml);
            }
        } catch (IOException e) {
            Log.warn("Failed to load mock profile for " + username, e);
        }

        // Fallback: Default profile
        return new QuestProfile(username, 1, 0, getXpForLevel(2), Collections.emptyList());
    }

    private QuestProfile parseProfile(String yaml) throws JsonProcessingException {
        // Simple DTO for YAML matching
        MockProfile mock = yamlMapper.readValue(yaml, MockProfile.class);

        int currentLevel = calculateLevel(mock.currentXp);
        int nextLevelXp = getXpForLevel(currentLevel + 1);

        List<QuestProfile.QuestHistoryItem> history = new ArrayList<>();
        if (mock.history != null) {
            for (MockHistoryItem item : mock.history) {
                history.add(new QuestProfile.QuestHistoryItem(item.title, item.xp, item.date));
            }
        }

        return new QuestProfile(mock.username, currentLevel, mock.currentXp, nextLevelXp, history);
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

    private int getXpForLevel(int level) {
        return levelConfigs.stream()
                .filter(l -> l.level == level)
                .map(l -> l.xpRequired)
                .findFirst()
                .orElse(99999); // Default high if max level reached
    }

    public List<Quest> getQuestBoard() {
        // Fetch open issues from os-santiago/community-directory (or main repo)
        // treating them as quests.
        // For v3.1.0 we look at 'quarkus-app' or general issues

        // Or better: use the current user's repo if running locally against fork?
        // Let's stick to os-santiago/open-quest or homedir if public.
        // Since homedir is private usually, let's target
        // os-santiago/community-directory issues as a demo source

        // Actually, let's use a static list for reliability if network fails or repo is
        // empty
        // But the plan promised "Open Issues".

        try {
            // Simplified GitHub API call - list issues
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/os-santiago/community-directory/issues?state=open"))
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                List<GithubIssue> issues = jsonMapper.readValue(response.body(),
                        new TypeReference<List<GithubIssue>>() {
                        });
                return mapIssuesToQuests(issues);
            } else {
                Log.error("Failed to fetch quests: " + response.statusCode());
            }

        } catch (Exception e) {
            Log.error("Error fetching quest board", e);
        }

        return Collections.emptyList();
    }

    private List<Quest> mapIssuesToQuests(List<GithubIssue> issues) {
        List<Quest> quests = new ArrayList<>();
        for (GithubIssue issue : issues) {
            // Calculate mock XP based on labels or randomness
            int xp = 50;
            String difficulty = "D"; // Default

            quests.add(new Quest(
                    String.valueOf(issue.number),
                    issue.title,
                    issue.body != null ? (issue.body.length() > 100 ? issue.body.substring(0, 100) + "..." : issue.body)
                            : "",
                    xp,
                    difficulty,
                    "OPEN",
                    issue.html_url));
        }
        return quests;
    }

    // Helper Classes for Serialization
    private static class GamificationConfig {
        public List<LevelConfig> levels;
    }

    private static class LevelConfig {
        public int level;
        public int xpRequired;
    }

    private static class MockProfile {
        public String username;
        public int currentXp;
        public List<MockHistoryItem> history;
    }

    private static class MockHistoryItem {
        public String title;
        public int xp;
        public String date;
    }

    // Minimal Issue representation
    public static class GithubIssue {
        public int number;
        public String title;
        public String body;
        public String html_url;
    }
}
