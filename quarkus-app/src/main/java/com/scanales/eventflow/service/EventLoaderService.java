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
public class EventLoaderService {

    private static final Logger LOG = Logger.getLogger(EventLoaderService.class);
    private static final String PREFIX = "[GIT] ";

    @Inject
    EventService eventService;

    private String repoUrl;
    private String branch;
    private String token;
    private Path localDir;
    private String dataDir;

    private boolean repoAvailable;

    private final GitLoadStatus status = new GitLoadStatus();

    @PostConstruct
    void init() {
        LOG.info(PREFIX + "EventLoaderService.init(): Iniciando carga de eventos desde Git");
        var cfg = ConfigProvider.getConfig();
        repoUrl = cfg.getOptionalValue("eventflow.sync.repoUrl", String.class).orElse(null);
        branch = cfg.getOptionalValue("eventflow.sync.branch", String.class).orElse("main");
        token = cfg.getOptionalValue("eventflow.sync.token", String.class).orElse(null);
        String dir = cfg.getOptionalValue("eventflow.sync.localDir", String.class)
                .orElse(System.getProperty("java.io.tmpdir") + "/eventflow-repo");
        dataDir = cfg.getOptionalValue("eventflow.sync.dataDir", String.class).orElse("event-data");
        localDir = Path.of(dir);
        status.setRepoUrl(repoUrl);
        status.setBranch(branch);

        LOG.debugf(PREFIX + "Repositorio: %s rama: %s dir local: %s", repoUrl, branch, localDir);

        reload();
    }

    private UsernamePasswordCredentialsProvider credentials() {
        return (token == null || token.isBlank()) ? null
                : new UsernamePasswordCredentialsProvider(token, "");
    }

    /** Attempts to reload events from the Git repository and updates status. */
    public synchronized GitLoadStatus reload() {
        status.setLastAttempt(java.time.LocalDateTime.now());
        if (repoUrl == null || repoUrl.isBlank()) {
            status.setSuccess(false);
            status.setMessage("repoUrl no configurado");
            LOG.error(PREFIX + "EventLoaderService.reload(): repoUrl no configurado");
            return status;
        }
        try {
            cloneOrPull();
            LoadMetrics m = loadEvents();
            repoAvailable = true;
            status.setSuccess(true);
            status.setMessage("Configuraci\u00f3n cargada correctamente desde Git.");
            status.setFilesRead(m.filesRead());
            status.setEventsImported(m.eventsImported());
            status.setLastSuccess(status.getLastAttempt());
        } catch (Exception e) {
            repoAvailable = false;
            status.setSuccess(false);
            status.setMessage(e.getMessage());
            LOG.error(PREFIX + "EventLoaderService.reload(): Error accediendo al repositorio", e);
        }
        return status;
    }

    private void cloneOrPull() throws GitAPIException, IOException {
        if (Files.exists(localDir.resolve(".git"))) {
            LOG.infof(PREFIX + "Pulling repository %s", repoUrl);
            try (Git git = Git.open(localDir.toFile())) {
                git.checkout().setName(branch).call();
                var pull = git.pull();
                if (credentials() != null) pull.setCredentialsProvider(credentials());
                pull.call();
            }
        } else {
            Files.createDirectories(localDir);
            LOG.infof(PREFIX + "Cloning repository %s", repoUrl);
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

    private record LoadMetrics(int filesRead, int eventsImported) {}

    private LoadMetrics loadEvents() {
        Path eventsPath = localDir.resolve(dataDir);
        if (!Files.exists(eventsPath)) {
            LOG.warnf(PREFIX + "Event directory %s not found", eventsPath);
            return new LoadMetrics(0, 0);
        }
        try (Stream<Path> stream = Files.list(eventsPath)) {
            var jsonFiles = stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .toList();
            LOG.infov(PREFIX + "EventLoaderService.loadEvents(): Se encontraron {0} archivos", jsonFiles.size());
            int imported = 0;
            int skipped = 0;
            try (Jsonb jsonb = JsonbBuilder.create()) {
                for (Path p : jsonFiles) {
                    if (importFile(jsonb, p)) {
                        imported++;
                    } else {
                        skipped++;
                    }
                }
            }
            LOG.infov(PREFIX + "Importados correctamente {0} archivos", imported);
            if (skipped > 0) {
                LOG.warnf(PREFIX + "Omitidos {0} archivos", skipped);
            }
            return new LoadMetrics(jsonFiles.size(), imported);
        } catch (Exception e) {
            LOG.error(PREFIX + "Error loading events from repo", e);
            return new LoadMetrics(0, 0);
        }
    }

    private boolean importFile(Jsonb jsonb, Path file) {
        try {
            Event event = jsonb.fromJson(Files.newInputStream(file), Event.class);
            if (event.getId() == null || event.getId().isBlank()) {
                LOG.errorf(PREFIX + "File %s missing id", file);
                return false;
            }
            if (eventService.getEvent(event.getId()) != null) {
                LOG.warnf(PREFIX + "Event %s already loaded, skipping", event.getId());
                return false;
            }
            fillDefaults(event);
            eventService.saveEvent(event);
            LOG.infov(PREFIX + "Imported event {0} from {1}", event.getId(), file);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, PREFIX + "Failed to import file %s", file);
            return false;
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
                String json = jsonb.toJson(event);
                Files.writeString(file, json);
                LOG.debug(PREFIX + "EventLoaderService.exportAndPushEvent(): " + json);
            }
            try (Git git = Git.open(localDir.toFile())) {
                git.add().addFilepattern(dataDir + "/event-" + event.getId() + ".json").call();
                git.commit().setMessage(message).call();
                var push = git.push();
                if (credentials() != null) push.setCredentialsProvider(credentials());
                push.call();
            }
            LOG.infov(PREFIX + "EventLoaderService.exportAndPushEvent(): Evento {0} enviado al repositorio", event.getId());
        } catch (Exception e) {
            LOG.error(PREFIX + "EventLoaderService.exportAndPushEvent(): Error al subir evento", e);
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
            LOG.infov(PREFIX + "EventLoaderService.removeEvent(): Evento {0} eliminado del repositorio", eventId);
        } catch (Exception e) {
            LOG.error(PREFIX + "EventLoaderService.removeEvent(): Error eliminando archivo", e);
        }
    }

    /** Returns the current Git load status. */
    public GitLoadStatus getStatus() {
        return status;
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
