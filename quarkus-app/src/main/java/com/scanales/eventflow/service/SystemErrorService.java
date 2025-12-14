package com.scanales.eventflow.service;

import com.scanales.eventflow.model.SystemError;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SystemErrorService {

    private static final Logger LOG = Logger.getLogger(SystemErrorService.class);

    @Inject
    PersistenceService persistence;

    private final Map<String, SystemError> errors = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        try {
            errors.putAll(persistence.loadSystemErrors());
        } catch (Exception e) {
            LOG.warn("Unable to load system errors; starting empty", e);
        }
    }

    public List<SystemError> findAllErrors() {
        List<SystemError> list = new ArrayList<>(errors.values());
        list.sort((a, b) -> b.createdAt.compareTo(a.createdAt)); // DESC
        return list;
    }

    public List<SystemError> findUnresolved() {
        return findAllErrors().stream().filter(e -> !e.resolved).toList();
    }

    public void logError(String severity, String source, String message, String stackTrace, String userId) {
        SystemError error = new SystemError(
                UUID.randomUUID().toString(),
                Instant.now(),
                severity != null ? severity : "ERROR",
                source != null ? source : "Unknown",
                message,
                stackTrace != null ? stackTrace : "",
                userId,
                false);
        errors.put(error.id, error);
        persist();
    }

    public void resolve(String id) {
        SystemError error = errors.get(id);
        if (error != null) {
            error.resolved = true;
            persist();
        }
    }

    private void persist() {
        try {
            persistence.saveSystemErrors(new ConcurrentHashMap<>(errors));
        } catch (Exception e) {
            LOG.error("Failed to persist system errors", e);
        }
    }
}
