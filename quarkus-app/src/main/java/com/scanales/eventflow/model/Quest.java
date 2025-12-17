package com.scanales.eventflow.model;

public record Quest(
                String id,
                String title,
                String description,
                int xpReward,
                String difficulty, // E, D, C, B, A, S, SS
                String status, // OPEN, IN_PROGRESS, CLOSED
                String url,
                java.util.List<String> assignees,
                java.util.List<String> labels) {
}
