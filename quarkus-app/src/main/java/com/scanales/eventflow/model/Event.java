package com.scanales.eventflow.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RegisterForReflection

/**
 * Represents an event with its basic information, the scenarios where the
 * activities take place and the agenda of talks.
 */
public class Event {

    @NotBlank
    private String id;
    @NotBlank
    private String title;
    @NotBlank
    private String description;
    private List<Scenario> scenarios = new ArrayList<>();
    /** Number of days the event lasts. */
    private int days = 1;
    /** URL or identifier for the venue map. */
    private String mapUrl;
    /** URL of the event logo. */
    private String logoUrl;
    /** Contact email for the event organizers. */
    @Email
    private String contactEmail;
    /** Official website of the event. */
    private String website;
    /** Link to the event's Twitter/X profile. */
    private String twitter;
    /** Link to the event's LinkedIn profile. */
    private String linkedin;
    /** Link to the event's Instagram profile. */
    private String instagram;
    /** URL to obtain tickets for the event. */
    private String ticketsUrl;
    private List<Talk> agenda = new ArrayList<>();
    /** Time when the event was created. */
    private LocalDateTime createdAt;
    /** Email of the user who created the event. */
    private String creator;
    /** Day when the event takes place. */
    private LocalDate date;

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

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getTwitter() {
        return twitter;
    }

    public void setTwitter(String twitter) {
        this.twitter = twitter;
    }

    public String getLinkedin() {
        return linkedin;
    }

    public void setLinkedin(String linkedin) {
        this.linkedin = linkedin;
    }

    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }

    public String getTicketsUrl() {
        return ticketsUrl;
    }

    public void setTicketsUrl(String ticketsUrl) {
        this.ticketsUrl = ticketsUrl;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDateStr() {
        return date == null ? "" : date.toString();
    }

    public void setDateStr(String value) {
        if (value != null && !value.isBlank()) {
            this.date = LocalDate.parse(value);
        } else {
            this.date = null;
        }
    }

    /** Returns the event date formatted for display, e.g. "5 de septiembre de 2025". */
    public String getFormattedDate() {
        if (date == null) {
            return "";
        }
        var formatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new java.util.Locale("es"));
        return date.format(formatter);
    }

    /** Returns the starting time of the event based on the earliest talk. */
    public LocalTime getStartTime() {
        return agenda.stream()
                .map(Talk::getStartTime)
                .filter(t -> t != null)
                .min(LocalTime::compareTo)
                .orElse(null);
    }

    /** Returns the ending time of the event based on the last scheduled talk. */
    public LocalTime getEndTime() {
        return agenda.stream()
                .map(Talk::getEndTime)
                .filter(t -> t != null)
                .max(LocalTime::compareTo)
                .orElse(null);
    }

    public String getStartTimeStr() {
        LocalTime t = getStartTime();
        return t == null ? "" : t.toString();
    }

    public String getEndTimeStr() {
        LocalTime t = getEndTime();
        return t == null ? "" : t.toString();
    }

    /**
     * Returns the number of days remaining from today until the event date.
     * If the event date is in the past or not defined, zero is returned.
     */
    public long getDaysUntil() {
        if (date == null) {
            return 0;
        }
        long diff = java.time.temporal.ChronoUnit.DAYS
                .between(java.time.LocalDate.now(), date);
        return diff < 0 ? 0 : diff;
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
     * Returns a short summary of the description limited to the first paragraph
     * and a maximum of 150 characters.
     */
    public String getDescriptionSummary() {
        if (description == null || description.isBlank()) {
            return "";
        }
        String summary = description.strip();
        int newline = summary.indexOf('\n');
        if (newline >= 0) {
            summary = summary.substring(0, newline);
        }
        if (summary.length() > 150) {
            summary = summary.substring(0, 150).trim() + "...";
        }
        return summary;
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

    /**
     * Returns the list of talks for the given {@code day} and
     * {@code scenarioId} ordered by start time. An empty list is returned when
     * there are no talks matching the provided parameters.
     */
    public java.util.List<Talk> getAgendaForDayAndScenario(int day, String scenarioId) {
        return agenda.stream()
                .filter(t -> t.getDay() == day && scenarioId.equals(t.getLocation()))
                .sorted(java.util.Comparator.comparing(Talk::getStartTime))
                .toList();
    }
}
