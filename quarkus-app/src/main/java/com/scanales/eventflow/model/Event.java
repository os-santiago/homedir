package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@RegisterForReflection

/**
 * Represents an event with its basic information, the scenarios where the
 * activities take place and the agenda of talks.
 */
public class Event {

    private String id;
    private String title;
    private String description;
    private List<Scenario> scenarios = new ArrayList<>();
    /** Number of days the event lasts. */
    private int days = 1;
    /** URL or identifier for the venue map. */
    private String mapUrl;
    private List<Talk> agenda = new ArrayList<>();
    /** Time when the event was created. */
    private LocalDateTime createdAt;
    /** Email of the user who created the event. */
    private String creator;

    public Event() {
    }

    public Event(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public Event(String id, String title, String description, LocalDateTime createdAt, String creator) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
        this.creator = creator;
    }

    public Event(String id, String title, String description, int days) {
        this(id, title, description);
        this.days = days;
    }

    public Event(String id, String title, String description, int days, LocalDateTime createdAt, String creator) {
        this(id, title, description, createdAt, creator);
        this.days = days;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Scenario> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<Scenario> scenarios) {
        this.scenarios = scenarios;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public String getMapUrl() {
        return mapUrl;
    }

    public void setMapUrl(String mapUrl) {
        this.mapUrl = mapUrl;
    }

    public List<Talk> getAgenda() {
        return agenda;
    }

    public void setAgenda(List<Talk> agenda) {
        this.agenda = agenda;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * Returns the creation date formatted for display in the UI.
     * If the date is not available, an empty string is returned.
     */
    public String getFormattedCreatedAt() {
        if (createdAt == null) {
            return "";
        }
        var formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", new java.util.Locale("es"));
        return createdAt.format(formatter);
    }

    /**
     * Returns a list with values from 1 to {@code days} to easily iterate in templates.
     */
    public java.util.List<Integer> getDayList() {
        return java.util.stream.IntStream.rangeClosed(1, days)
                .boxed()
                .toList();
    }

    /**
     * Retrieves the name of a scenario given its id, or {@code null} if not found.
     */
    public String getScenarioName(String scenarioId) {
        return scenarios.stream()
                .filter(s -> s.getId().equals(scenarioId))
                .map(Scenario::getName)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the list of talks for the given day ordered by start time.
     */
    public java.util.List<Talk> getAgendaForDay(int day) {
        return agenda.stream()
                .filter(t -> t.getDay() == day)
                .sorted(java.util.Comparator.comparing(Talk::getStartTime))
                .toList();
    }
}
