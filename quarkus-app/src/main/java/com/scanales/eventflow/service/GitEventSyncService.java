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
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.scanales.eventflow.model.Event;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Loads event definitions from a Git repository when the application starts.
 */
@ApplicationScoped
@io.quarkus.runtime.Startup
public class GitEventSyncService {

    private static final Logger LOG = Logger.getLogger(GitEventSyncService.class);
    private static final String PREFIX = "[GITSYNC] ";

    @Inject
    EventService eventService;

    private String repoUrl;
    private String branch;
    private String folder;
    private Path localPath;

    @PostConstruct
    void init() {
        var cfg = ConfigProvider.getConfig();
        repoUrl = cfg.getOptionalValue("eventflow.git.repo.url", String.class).orElse(null);
        branch = cfg.getOptionalValue("eventflow.git.branch", String.class).orElse("main");
        folder = cfg.getOptionalValue("eventflow.git.folder", String.class).orElse("events");
        String lp = cfg.getOptionalValue("eventflow.git.local.path", String.class)
                .orElse("/tmp/eventflow-repo");
        localPath = Path.of(lp);

        LOG.infof(PREFIX + "Repositorio %s rama %s en %s", repoUrl, branch, localPath);

        try {
            cloneOrPull();
            loadEvents();
        } catch (Exception e) {
            LOG.error(PREFIX + "Error al sincronizar repo", e);
            LOG.warn(PREFIX + "No se pudo conectar al repo, iniciando sin eventos");
        }
    }

    private void cloneOrPull() throws GitAPIException, IOException {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IOException("URL del repositorio no configurada");
        }
        if (Files.exists(localPath.resolve(".git"))) {
            LOG.info(PREFIX + "Repo ya clonado, haciendo pull...");
            try (Git git = Git.open(localPath.toFile())) {
                git.checkout().setName(branch).call();
                git.pull().call();
            }
        } else {
            LOG.info(PREFIX + "Clonando repo...");
            Files.createDirectories(localPath);
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(localPath.toFile())
                    .setBranch(branch)
                    .call()
                    .close();
        }
    }

    private void loadEvents() {
        loadEvents(null);
    }

    private void loadEvents(GitSyncResult result) {
        Path eventsDir = localPath.resolve(folder);
        if (!Files.exists(eventsDir)) {
            LOG.warnf(PREFIX + "Directorio de eventos %s no encontrado", eventsDir);
            if (result != null) {
                result.message = "Directorio de eventos no encontrado";
            }
            return;
        }
        int count = 0;
        try (Stream<Path> files = Files.list(eventsDir); Jsonb jsonb = JsonbBuilder.create()) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                LOG.infof(PREFIX + "Cargando archivo %s...", f.getFileName());
                try (var in = Files.newInputStream(f)) {
                    Event ev = jsonb.fromJson(in, Event.class);
                    eventService.putEvent(ev.getId(), ev);
                    if (result != null) {
                        result.filesLoaded.add(f.getFileName().toString());
                    }
                    LOG.infof(PREFIX + "Evento %s cargado correctamente.", ev.getId());
                    count++;
                } catch (Exception e) {
                    LOG.errorf(e, PREFIX + "Error procesando %s", f.getFileName());
                    if (result != null) {
                        result.errors.add(f.getFileName().toString());
                    }
                }
            }
        } catch (Exception e) {
            LOG.error(PREFIX + "Error leyendo archivos de eventos", e);
            if (result != null) {
                result.errors.add(e.getMessage());
            }
        }
        LOG.infof(PREFIX + "Total eventos cargados: %d", count);
    }

    public GitSyncResult reloadEventsFromGit() {
        LOG.info(PREFIX + "Recarga de eventos solicitada desde panel");
        GitSyncResult res = new GitSyncResult();
        try {
            cloneOrPull();
            loadEvents(res);
            if (res.errors.isEmpty()) {
                res.success = true;
                res.message = "Se cargaron " + res.filesLoaded.size() + " eventos desde Git";
            } else {
                res.success = false;
                res.message = "Hubo errores cargando " + res.errors.size() + " archivos. Ver detalles";
            }
        } catch (Exception e) {
            LOG.error(PREFIX + "Error al recargar eventos desde Git", e);
            res.success = false;
            res.message = "No se pudo acceder al repositorio remoto";
            res.errors.add(e.getMessage());
        }
        return res;
    }
}

