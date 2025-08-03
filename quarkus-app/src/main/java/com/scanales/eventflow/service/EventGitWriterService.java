package com.scanales.eventflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.graalvm.nativeimage.ImageInfo;
import org.jboss.logging.Logger;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.util.EventUtils;

/**
 * Writes event definitions to a local Git repository and pushes the changes.
 */
@ApplicationScoped
public class EventGitWriterService {

    private static final Logger LOG = Logger.getLogger(EventGitWriterService.class);
    private static final String PREFIX = "[GITWRITE] ";

    private Path localDir;
    private String dataDir;

    @Inject
    GitLogService gitLog;

    @PostConstruct
    void init() {
        var cfg = ConfigProvider.getConfig();
        String repoUrl = cfg.getOptionalValue("eventflow.sync.repoUrl", String.class).orElse(null);
        dataDir = cfg.getOptionalValue("eventflow.sync.dataDir", String.class)
                .orElse("event-data");

        String repoName = (repoUrl != null && !repoUrl.isBlank())
                ? repoUrl.substring(repoUrl.lastIndexOf('/') + 1)
                : "event-repo";
        if (repoName.endsWith(".git")) {
            repoName = repoName.substring(0, repoName.length() - 4);
        }
        localDir = Path.of(System.getProperty("java.io.tmpdir"), repoName);

        gitLog.log("Git writer init path=" + localDir + " dataDir=" + dataDir);
    }

    /**
     * Persists the given event to the Git repository.
     *
     * @param event the event to persist
     * @param updatedByEmail email of the admin performing the change
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public synchronized boolean persistEventToGit(Event event, String updatedByEmail) {
        if (!EventUtils.hasRequiredData(event)) {
            LOG.warn(PREFIX + "Evento " + (event != null ? event.getId() : "null")
                    + " incompleto, no se guarda en Git");
            return false;
        }

        if (ImageInfo.inImageRuntimeCode()) {
            LOG.info(PREFIX + "Native image runtime detected.");
        }

        try {
            Path eventsDir = localDir.resolve(dataDir);
            Files.createDirectories(eventsDir);

            JsonbConfig cfg = new JsonbConfig().withFormatting(true);
            String json;
            try (Jsonb jsonb = JsonbBuilder.create(cfg)) {
                json = jsonb.toJson(event);
            }

            Path file = eventsDir.resolve(event.getId() + ".json");
            if (Files.exists(file)) {
                String existing = Files.readString(file);
                if (existing.equals(json)) {
                    LOG.infov(PREFIX + "Evento {0} sin cambios, no se realiza commit", event.getId());
                    return true;
                }
            }
            Files.writeString(file, json, StandardCharsets.UTF_8);

            var repoDir = localDir.toFile();
            if (!repoDir.exists()) {
                throw new RepositoryNotFoundException("Directory not found: " + repoDir);
            }
            try (Git git = Git.open(repoDir)) {
                git.add().addFilepattern(dataDir + "/" + event.getId() + ".json").call();
                if (git.status().call().isClean()) {
                    LOG.infov(PREFIX + "Evento {0} sin cambios en Git", event.getId());
                    gitLog.log("Event " + event.getId() + " no changes");
                    return true;
                }
                String msg = "chore(event): updated event " + event.getId() + " by " + updatedByEmail;
                git.commit().setMessage(msg).call();
                git.push().call();
            }

            LOG.infov(PREFIX + "\u2705 Evento {0} guardado en Git por {1}", event.getId(), updatedByEmail);
            gitLog.log("Event " + event.getId() + " pushed by " + updatedByEmail);
            return true;
        } catch (RepositoryNotFoundException e) {
            LOG.errorf(PREFIX + "Repository not found: {0}", e.getMessage());
            gitLog.log("Repository not found: " + e.getMessage());
            return false;
        } catch (IOException | GitAPIException | jakarta.json.bind.JsonbException e) {
            LOG.errorf(e, PREFIX + "\u274c Error al guardar evento {0} en Git: {1}", event.getId(), e.getMessage());
            gitLog.log("Error saving event " + event.getId() + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            LOG.errorf(e, PREFIX + "\u274c Error inesperado al guardar evento {0} en Git: {1}",
                    event != null ? event.getId() : "null", e.getMessage());
            gitLog.log("Unexpected error saving event " + (event != null ? event.getId() : "null") + ": " + e.getMessage());
            return false;
        }
    }
}

