package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)

/**
 * Describes a talk within an event.
 */
public class Talk {

    private String id;
    private String name;
    private String description;
    private Speaker speaker;
    private Speaker coSpeaker;
    /** Room or scenario where the talk happens. */
    private String location;
    /** Day within the event when the talk occurs (1-based). */
    private int day = 1;
    private LocalTime startTime;
    /** Duration in minutes. */
    private int durationMinutes;

    public Talk() {
    }

    public Talk(String id, String name) {
        this.id = id;
        this.name = name;
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
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Speaker getSpeaker() {
        return speaker;
    }

    public void setSpeaker(Speaker speaker) {
        this.speaker = speaker;
    }

    public Speaker getCoSpeaker() {
        return coSpeaker;
    }

    public void setCoSpeaker(Speaker coSpeaker) {
        this.coSpeaker = coSpeaker;
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
}
