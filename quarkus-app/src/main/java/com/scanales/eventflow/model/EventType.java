package com.scanales.eventflow.model;

import java.util.Arrays;

/** Classifies events by type without relying on database enums. */
public enum EventType {
  CONFERENCE("Conferencia"),
  MEETUP("Meetup"),
  WORKSHOP("Workshop"),
  HACKATHON("Hackatón"),
  OTHER("General");

  private final String label;

  EventType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  public String getCssClass() {
    return "event-type-" + name().toLowerCase();
  }

  public static EventType fromNullable(String value) {
    if (value == null || value.isBlank()) {
      return OTHER;
    }
    return Arrays.stream(values())
        .filter(t -> t.name().equalsIgnoreCase(value))
        .findFirst()
        .orElse(OTHER);
  }
}
