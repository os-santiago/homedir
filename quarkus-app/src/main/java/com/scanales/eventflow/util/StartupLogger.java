package com.scanales.eventflow.util;

import org.jboss.logging.Logger;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/** Logs application startup events. */
@ApplicationScoped
public class StartupLogger {

    private static final Logger LOG = Logger.getLogger(StartupLogger.class);
    private static final String PREFIX = "[BOOT] ";

    void onStart(@Observes StartupEvent ev) {
        LOG.info(PREFIX + "Application started");
    }
}
