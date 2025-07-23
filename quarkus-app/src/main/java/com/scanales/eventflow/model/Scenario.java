package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Defines a scenario or room where the event activities take place.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Scenario {

    private String id;
    private String name;
    private String features;
    private String location;

    public Scenario() {
    }

    public Scenario(String id, String name) {
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

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
