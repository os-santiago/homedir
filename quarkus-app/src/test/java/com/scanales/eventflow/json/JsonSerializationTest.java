package com.scanales.eventflow.json;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.scanales.eventflow.model.Event;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class JsonSerializationTest {

    @Inject
    ObjectMapper mapper;

    @Test
    void serializationExcludesNulls() throws Exception {
        Event e = new Event();
        e.setId("e1");
        e.setTitle("Sample");
        String json = mapper.writeValueAsString(e);
        assertFalse(json.contains("description"));
    }

    @Test
    void unknownPropertiesFail() {
        String json = "{\"id\":\"e1\",\"title\":\"Test\",\"extra\":1}";
        assertThrows(UnrecognizedPropertyException.class, () -> mapper.readValue(json, Event.class));
    }
}
