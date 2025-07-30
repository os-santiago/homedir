package com.scanales.eventflow.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

/**
 * Synchronizes events with a Git repository acting as source of truth.
 */
@ApplicationScoped
public class GitEventSyncService {

    private static final Logger LOG = Logger.getLogger(GitEventSyncService.class);

    @Inject
    EventService eventService;

    private String repoUrl;
    private String branch;
    private String token;
    private Path localDir;
    private String dataDir;

    private boolean repoAvailable;

    @PostConstruct
    void init() {
        var cfg = ConfigProvider.getConfig();
        repoUrl = cfg.getOptionalValue("eventflow.sync.repoUrl", String.class).orElse(null);
        branch = cfg.getOptionalValue("eventflow.sync.branch", String.class).orElse("main");
        token = cfg.getOptionalValue("eventflow.sync.token", String.class).orElse(null);
        String dir = cfg.getOptionalValue("eventflow.sync.localDir", String.class)
                .orElse(System.getProperty("java.io.tmpdir") + "/eventflow-repo");
        dataDir = cfg.getOptionalValue("eventflow.sync.dataDir", String.class).orElse("event-data");
        localDir = Path.of(dir);

        if (repoUrl == null || repoUrl.isBlank()) {
            LOG.info("Event sync repo URL not configured, skipping clone");
            return;
        }

        try {
            cloneOrPull();
            loadEvents();
            repoAvailable = true;
        } catch (Exception e) {
            LOG.error("Failed to initialize Git synchronization", e);
        }
    }

    private UsernamePasswordCredentialsProvider credentials() {
        return (token == null || token.isBlank()) ? null
                : new UsernamePasswordCredentialsProvider(token, "");
    }

    private void cloneOrPull() throws GitAPIException, IOException {
        if (Files.exists(localDir.resolve(".git"))) {
            LOG.infof("Pulling repository %s", repoUrl);
            try (Git git = Git.open(localDir.toFile())) {
                git.checkout().setName(branch).call();
                var pull = git.pull();
                if (credentials() != null) pull.setCredentialsProvider(credentials());
                pull.call();
            }
        } else {
            Files.createDirectories(localDir);
            LOG.infof("Cloning repository %s", repoUrl);
            var clone = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(localDir.toFile())
                    .setBranch(branch);
            if (credentials() != null) clone.setCredentialsProvider(credentials());
            try (Git git = clone.call()) {
                // nothing
            }
        }
    }

    private void loadEvents() {
        Path eventsPath = localDir.resolve(dataDir);
        if (!Files.exists(eventsPath)) {
            LOG.warnf("Event directory %s not found", eventsPath);
            return;
        }
        try (Stream<Path> files = Files.list(eventsPath);
             Jsonb jsonb = JsonbBuilder.create()) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> importFile(jsonb, p));
        } catch (Exception e) {
            LOG.error("Error loading events from repo", e);
        }
    }

    private void importFile(Jsonb jsonb, Path file) {
        try {
            Event event = jsonb.fromJson(Files.newInputStream(file), Event.class);
            if (event.getId() == null || event.getId().isBlank()) {
                LOG.errorf("File %s missing id", file);
                return;
            }
            if (eventService.getEvent(event.getId()) != null) {
                LOG.warnf("Event %s already loaded, skipping", event.getId());
                return;
            }
            fillDefaults(event);
            eventService.saveEvent(event);
            LOG.infov("Imported event {0} from {1}", event.getId(), file);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to import file %s", file);
        }
    }

    /** Writes the event JSON to the repository and pushes changes. */
    public void exportAndPushEvent(Event event, String message) {
        if (!repoAvailable) return;
        Path eventsPath = localDir.resolve(dataDir);
        try {
            Files.createDirectories(eventsPath);
            JsonbConfig cfg = new JsonbConfig().withFormatting(true);
            try (Jsonb jsonb = JsonbBuilder.create(cfg)) {
                Path file = eventsPath.resolve("event-" + event.getId() + ".json");
                Files.writeString(file, jsonb.toJson(event));
            }
            try (Git git = Git.open(localDir.toFile())) {
                git.add().addFilepattern(dataDir + "/event-" + event.getId() + ".json").call();
                git.commit().setMessage(message).call();
                var push = git.push();
                if (credentials() != null) push.setCredentialsProvider(credentials());
                push.call();
            }
            LOG.infov("Pushed event {0} to repo", event.getId());
        } catch (Exception e) {
            LOG.error("Failed to push event to repo", e);
        }
    }

    /** Removes the event file from the repository and pushes the change. */
    public void removeEvent(String eventId, String message) {
        if (!repoAvailable) return;
        Path file = localDir.resolve(dataDir).resolve("event-" + eventId + ".json");
        try {
            Files.deleteIfExists(file);
            try (Git git = Git.open(localDir.toFile())) {
                git.rm().addFilepattern(dataDir + "/event-" + eventId + ".json").call();
                git.commit().setMessage(message).call();
                var push = git.push();
                if (credentials() != null) push.setCredentialsProvider(credentials());
                push.call();
            }
            LOG.infov("Removed event {0} from repo", eventId);
        } catch (Exception e) {
            LOG.error("Failed to remove event file", e);
        }
    }

    /** Copied from AdminEventResource to apply default values. */
    private void fillDefaults(Event event) {
        if (event.getTitle() == null) event.setTitle("VACIO");
        if (event.getDescription() == null) event.setDescription("VACIO");
        if (event.getMapUrl() == null) event.setMapUrl("VACIO");
        if (event.getEventDate() == null) event.setEventDate(java.time.LocalDate.now());
        if (event.getCreator() == null) event.setCreator("VACIO");
        if (event.getCreatedAt() == null) event.setCreatedAt(java.time.LocalDateTime.now());

        if (event.getScenarios() != null) {
            for (Scenario sc : event.getScenarios()) {
                if (sc.getName() == null) sc.setName("VACIO");
                if (sc.getFeatures() == null) sc.setFeatures("VACIO");
                if (sc.getLocation() == null) sc.setLocation("VACIO");
                if (sc.getMapUrl() == null) sc.setMapUrl("VACIO");
                if (sc.getId() == null) sc.setId(java.util.UUID.randomUUID().toString());
            }
        }

        if (event.getAgenda() != null) {
            for (Talk t : event.getAgenda()) {
                if (t.getName() == null) t.setName("VACIO");
                if (t.getDescription() == null) t.setDescription("VACIO");
                if (t.getLocation() == null) t.setLocation("VACIO");
                if (t.getStartTime() == null) t.setStartTime(java.time.LocalTime.MIDNIGHT);
                if (t.getSpeaker() == null) t.setSpeaker(new com.scanales.eventflow.model.Speaker("","VACIO"));
            }
        }
    }
}
