package com.scanales.homedir.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum QuestClass {
    ENGINEER("Engineer", "🛡️", "Architects of robust platforms."),
    SCIENTIST("Scientist", "🔬", "Innovators in data and AI."),
    WARRIOR("Warrior", "⚔️", "Champions of app dev and security."),
    MAGE("Mage", "🪄", "Masters of automation and UX.");

    private final String displayName;
    private final String emoji;
    private final String description;

    QuestClass(String displayName, String emoji, String description) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getDescription() {
        return description;
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }

    @JsonCreator
    public static QuestClass fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return QuestClass.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
