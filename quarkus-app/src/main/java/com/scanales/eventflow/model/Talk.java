package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id",
        resolver = PermissiveObjectIdResolver.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@RegisterForReflection

/**
 * Describes a talk within an event.
 */
public class Talk {

    @NotBlank
    private String id;

    public static final int MAX_NAME_LENGTH = 120;

    @NotBlank
    @Size(max = MAX_NAME_LENGTH)
    private String name;
    private String description;
    private List<Speaker> speakers = new ArrayList<>();
    /** Room or scenario where the talk happens. */
    private String location;
    /** Day within the event when the talk occurs (1-based). */
    private int day = 1;
    private LocalTime startTime;
    /** Duration in minutes. */
    private int durationMinutes;
    /** Indicates whether this entry represents a break (coffee, lunch, etc.). */
    private boolean breakSlot;

    public Talk() {
    }

    public Talk(String id, String name) {
        this.id = id;
        setName(name);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = sanitizeName(name);
    }

    /**
     * Sanitizes talk names by replacing control characters with spaces,
     * stripping unsupported symbols and normalizing whitespace. The result
     * is truncated to {@link #MAX_NAME_LENGTH} characters.
     */
    public static String sanitizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        // Replace control characters with spaces to preserve word boundaries
        normalized = normalized.replaceAll("\\p{Cntrl}", " ");
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}\\p{Punct} ]", "");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            normalized = normalized.substring(0, MAX_NAME_LENGTH);
        }
        return normalized;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Speaker> getSpeakers() {
        return speakers;
    }

    public void setSpeakers(List<Speaker> speakers) {
        this.speakers = speakers;
    }

    @JsonIgnore
    public String getSpeakerNames() {
        return speakers.stream()
                .map(Speaker::getName)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getStartTimeStr() {
        return startTime == null ? "" : startTime.toString();
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public void setStartTimeStr(String value) {
        if (value != null && !value.isBlank()) {
            this.startTime = LocalTime.parse(value);
        } else {
            this.startTime = null;
        }
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public LocalTime getEndTime() {
        return startTime == null ? null : startTime.plusMinutes(durationMinutes);
    }

    public String getEndTimeStr() {
        LocalTime end = getEndTime();
        return end == null ? "" : end.toString();
    }

    public boolean isBreak() {
        return breakSlot;
    }

    public void setBreak(boolean breakSlot) {
        this.breakSlot = breakSlot;
    }
}
