package com.scanales.eventflow.model;

import java.util.List;

public class QuestProfile {
    public String username;
    public int level;
    public int currentXp;
    public int nextLevelXp;
    public List<QuestHistoryItem> history;

    public QuestProfile() {
    }

    public QuestProfile(String username, int level, int currentXp, int nextLevelXp, List<QuestHistoryItem> history) {
        this.username = username;
        this.level = level;
        this.currentXp = currentXp;
        this.nextLevelXp = nextLevelXp;
        this.history = history;
    }

    public static class QuestHistoryItem {
        public String title;
        public int xp;
        public String date;

        public QuestHistoryItem() {
        }

        public QuestHistoryItem(String title, int xp, String date) {
            this.title = title;
            this.xp = xp;
            this.date = date;
        }
    }
}
