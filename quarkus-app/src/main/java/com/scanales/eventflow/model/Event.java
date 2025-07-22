package com.scanales.eventflow.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an event with its basic information, the scenarios where the
 * activities take place and the agenda of talks.
 */
public class Event {

    private String id;
    private String title;
    private String description;
    private List<Scenario> scenarios = new ArrayList<>();
    /** URL or identifier for the venue map. */
    private String mapUrl;
    private List<Talk> agenda = new ArrayList<>();

    public Event() {
    }

    public Event(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
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
}
