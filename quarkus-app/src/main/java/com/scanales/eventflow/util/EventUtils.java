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
        if (event.getStartDate() == null) event.setStartDate(java.time.LocalDate.now());
        if (event.getCreator() == null) event.setCreator("VACIO");
        if (event.getCreatedAt() == null) event.setCreatedAt(java.time.LocalDateTime.now());

        if (event.getScenarios() != null) {
            for (Scenario sc : event.getScenarios()) {
                if (sc.getName() == null) sc.setName("VACIO");
                if (sc.getFeatures() == null) sc.setFeatures("VACIO");
                if (sc.getLocation() == null) sc.setLocation("VACIO");
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

    /**
     * Checks that the event contains the minimum required data before being
     * exported or persisted to Git. An event must have a non-blank identifier
     * and at least one scenario or talk defined.
     *
     * @param event event to validate
     * @return {@code true} if the event has enough data, {@code false} otherwise
     */
    public static boolean hasRequiredData(Event event) {
        if (event == null) {
            return false;
        }

        boolean hasLists = (event.getScenarios() != null && !event.getScenarios().isEmpty())
                || (event.getAgenda() != null && !event.getAgenda().isEmpty());

        return event.getId() != null && !event.getId().isBlank() && hasLists;
    }
}
