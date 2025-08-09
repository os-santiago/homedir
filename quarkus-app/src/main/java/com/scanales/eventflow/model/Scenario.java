package com.scanales.eventflow.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

/**
 * Defines a scenario or room where the event activities take place.
 */
@RegisterForReflection
public class Scenario {

    @NotBlank
    private String id;
    @NotBlank
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
