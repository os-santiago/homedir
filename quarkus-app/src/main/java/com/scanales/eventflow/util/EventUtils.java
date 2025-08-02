package com.scanales.eventflow.util;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;

/** Utility methods for event operations. */
public final class EventUtils {

    private EventUtils() {
    }

    /**
     * Applies default values to optional fields so that templates and
     * JSON serialization do not encounter null references.
     */
    public static void fillDefaults(Event event) {
        if (event == null) return;

        if (event.getTitle() == null) event.setTitle("VACIO");
        if (event.getDescription() == null) event.setDescription("VACIO");
        if (event.getMapUrl() == null) event.setMapUrl("VACIO");
        if (event.getStartDate() == null) event.setStartDate(java.time.LocalDate.now());
        if (event.getCreator() == null) event.setCreator("VACIO");
        if (event.getCreatedAt() == null) event.setCreatedAt(java.time.LocalDateTime.now());

        if (event.getScenarios() != null) {
            for (Scenario sc : event.getScenarios()) {
                if (sc.getName() == null) sc.setName("VACIO");
                if (sc.getFeatures() == null) sc.setFeatures("VACIO");
                if (sc.getLocation() == null) sc.setLocation("VACIO");
                if (sc.getMapUrl() == null) sc.setMapUrl("VACIO");
                if (sc.getId() == null) sc.setId(java.util.UUID.randomUUID().toString());
            }
        }

        if (event.getAgenda() != null) {
            for (Talk t : event.getAgenda()) {
                if (t.getName() == null) t.setName("VACIO");
                if (t.getDescription() == null) t.setDescription("VACIO");
                if (t.getLocation() == null) t.setLocation("VACIO");
                if (t.getStartTime() == null) t.setStartTime(java.time.LocalTime.MIDNIGHT);
                if (t.getSpeaker() == null) t.setSpeaker(new com.scanales.eventflow.model.Speaker("", "VACIO"));
            }
        }
    }
}
