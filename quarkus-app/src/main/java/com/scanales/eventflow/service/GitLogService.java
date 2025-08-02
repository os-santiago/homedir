package com.scanales.eventflow.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

/**
 * Simple service that records git related operations into a log file so it can
 * be downloaded from the admin panel.
 */
@ApplicationScoped
public class GitLogService {

    private static final Logger LOG = Logger.getLogger(GitLogService.class);

    @Inject
    Config config;

    private Path logFile;

    @PostConstruct
    void init() {
        String path = config.getOptionalValue("eventflow.git.log.file", String.class)
                .orElse(System.getProperty("java.io.tmpdir") + "/git-operations.log");
        logFile = Path.of(path);
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            LOG.warn("Unable to create directories for git log file", e);
        }
    }

    /**
     * Appends the given message with timestamp to the log file.
     */
    public synchronized void log(String message) {
        String line = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                + " - " + message + System.lineSeparator();
        try {
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Failed to write git log", e);
        }
    }

    public Path getLogFile() {
        return logFile;
    }
}

