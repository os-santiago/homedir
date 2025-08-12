package com.scanales.eventflow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AppTemplateExtensionsZoneDetailTest {

    @Test
    void zoneDetailProvidesOffset() {
        assertEquals("UTC", AppTemplateExtensions.zoneDetail("UTC"));
        String detail = AppTemplateExtensions.zoneDetail("America/Santiago");
        assertTrue(detail.startsWith("UTC-"));
    }
}

