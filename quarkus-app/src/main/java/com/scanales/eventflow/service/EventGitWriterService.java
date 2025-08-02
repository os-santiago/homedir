package com.scanales.eventflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.scanales.eventflow.model.Event;

/**
 * Writes event definitions to a local Git repository and pushes the changes.
 */
@ApplicationScoped
public class EventGitWriterService {

    private static final Logger LOG = Logger.getLogger(EventGitWriterService.class);
    private static final String PREFIX = "[GITWRITE] ";

    private Path localPath;
    private String folder;

    @PostConstruct
    void init() {
        var cfg = ConfigProvider.getConfig();
        String lp = cfg.getOptionalValue("eventflow.git.local.path", String.class)
                .orElse("/tmp/eventflow-repo");
        folder = cfg.getOptionalValue("eventflow.git.folder", String.class)
                .orElse("events");
        localPath = Path.of(lp);
    }

    /**
     * Persists the given event to the Git repository.
     *
     * @param event the event to persist
     * @param updatedByEmail email of the admin performing the change
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean persistEventToGit(Event event, String updatedByEmail) {
        try {
            Path eventsDir = localPath.resolve(folder);
            Files.createDirectories(eventsDir);
            JsonbConfig cfg = new JsonbConfig().withFormatting(true);
            try (Jsonb jsonb = JsonbBuilder.create(cfg)) {
                Path file = eventsDir.resolve(event.getId() + ".json");
                String json = jsonb.toJson(event);
                Files.writeString(file, json, StandardCharsets.UTF_8);
            }
            try (Git git = Git.open(localPath.toFile())) {
                git.add().addFilepattern(folder + "/" + event.getId() + ".json").call();
                String msg = "update: " + event.getId() + " updated by " + updatedByEmail;
                git.commit().setMessage(msg).call();
                git.push().call();
            }
            LOG.infov(PREFIX + "Evento {0} persistido en Git", event.getId());
            return true;
        } catch (IOException | GitAPIException | jakarta.json.bind.JsonbException e) {
            LOG.error(PREFIX + "Error al persistir evento en Git", e);
            return false;
        } catch (Exception e) {
            LOG.error(PREFIX + "Error inesperado al persistir evento en Git", e);
            return false;
        }
    }
}

