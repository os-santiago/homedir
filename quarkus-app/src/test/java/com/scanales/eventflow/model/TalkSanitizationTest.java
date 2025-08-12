package com.scanales.eventflow.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Tests for Talk name sanitization. */
public class TalkSanitizationTest {

    @Test
    void removesInvalidCharacters() {
        String raw = "  Name\tWith\u0000Controls \uD83D\uDC7E and emoji  ";
        Talk talk = new Talk("id1", raw);
        assertEquals("Name WithControls and emoji", talk.getName());
    }

    @Test
    void truncatesToMaxLength() {
        String longName = "a".repeat(Talk.MAX_NAME_LENGTH + 5);
        Talk talk = new Talk("id2", longName);
        assertEquals(Talk.MAX_NAME_LENGTH, talk.getName().length());
    }
}

