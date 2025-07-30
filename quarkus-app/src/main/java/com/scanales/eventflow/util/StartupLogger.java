package com.scanales.eventflow.util;

import org.jboss.logging.Logger;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.LaunchMode;
import org.eclipse.microprofile.config.ConfigProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/** Logs application startup events. */
@ApplicationScoped
public class StartupLogger {

    private static final Logger LOG = Logger.getLogger(StartupLogger.class);
    private static final String PREFIX = "[BOOT] ";

    void onStart(@Observes StartupEvent ev) {
        String mode = LaunchMode.current().toString().toLowerCase();
        String repoUrl = ConfigProvider.getConfig()
                .getOptionalValue("eventflow.sync.repoUrl", String.class)
                .orElse("not configured");
        String ts = java.time.LocalDateTime.now().toString();
        LOG.infov(PREFIX + "StartupLogger.onStart(): Aplicaci\u00f3n iniciada en modo {0} a las {1}", mode, ts);
        LOG.infov(PREFIX + "eventflow.sync.repoUrl={0}", repoUrl);
    }
}
