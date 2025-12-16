package com.scanales.eventflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
                    .uri(URI.create(
                            String.format("https://api.github.com/repos/%s/%s/issues?state=open", repoOwner, repoName)))
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

    List<Quest> mapIssuesToQuests(List<GithubIssue> issues) {
        List<Quest> quests = new ArrayList<>();
        for (GithubIssue issue : issues) {
            // Calculate mock XP based on labels or randomness
            int xp = 50;
            String difficulty = "D";
            String status = "OPEN";

            if (issue.labels != null) {
                for (GithubLabel label : issue.labels) {
                    if (label.name.startsWith("xp:")) {
                        try {
                            xp = Integer.parseInt(label.name.substring(3));
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    } else if (label.name.startsWith("difficulty:")) {
                        difficulty = label.name.substring(11).toUpperCase();
                    } else if (label.name.startsWith("status:")) {
                        status = label.name.substring(7).toUpperCase();
                    }
                }
            }

            List<String> assignees = new ArrayList<>();
            if (issue.assignees != null) {
                for (GithubUser user : issue.assignees) {
                    assignees.add(user.login);
                }
            }

            quests.add(new Quest(
                    String.valueOf(issue.number),
                    issue.title,
                    issue.body != null ? (issue.body.length() > 100 ? issue.body.substring(0, 100) + "..." : issue.body)
                            : "",
                    xp,
                    difficulty,
                    status,
                    issue.html_url,
                    assignees));
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

    // Minimal Issue representation
    public static class GithubIssue {
        public int number;
        public String title;
        public String body;
        public String html_url;
        public List<GithubLabel> labels;
        public List<GithubUser> assignees;
    }

    public static class GithubUser {
        public String login;
    }

    public static class GithubLabel {
        public String name;
    }
}
