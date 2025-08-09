package com.scanales.eventflow.private_;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.service.PersistenceService;
import com.scanales.eventflow.util.AdminUtils;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/private/admin/backup")
public class AdminBackupResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance index(String message);
    }

    @Inject
    SecurityIdentity identity;

    @Inject
    EventService eventService;

    @Inject
    SpeakerService speakerService;

    @Inject
    PersistenceService persistence;

    @ConfigProperty(name = "quarkus.application.version")
    String appVersion;

    private static final Logger LOG = Logger.getLogger(AdminBackupResource.class);

    private boolean isAdmin() {
        return AdminUtils.isAdmin(identity);
    }

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response index(@QueryParam("msg") String message) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(Templates.index(message)).build();
    }

    @GET
    @Path("/download")
    @Authenticated
    @Produces("application/zip")
    public Response download() {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        try {
            persistence.flush();
            java.nio.file.Path dataDir = Paths.get(System.getProperty("eventflow.data.dir", "data"));
            if (!Files.exists(dataDir)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("\u274c Error: No hay datos para respaldar.")
                        .type(MediaType.TEXT_PLAIN + ";charset=UTF-8")
                        .build();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                try (var stream = Files.list(dataDir)) {
                    stream.filter(Files::isRegularFile).forEach(p -> {
                        try (InputStream in = Files.newInputStream(p)) {
                            zos.putNextEntry(new ZipEntry(p.getFileName().toString()));
                            in.transferTo(zos);
                            zos.closeEntry();
                        } catch (Exception e) {
                            LOG.error("Failed to add file to backup", e);
                        }
                    });
                }
            }
            byte[] data = baos.toByteArray();
            String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now());
            String filename = String.format("backup_%s_v%s.zip", ts, appVersion);
            LOG.infov("Generated backup {0}", filename);
            return Response.ok(data)
                    .header("Content-Disposition", "attachment; filename=" + filename)
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to generate backup", e);
            return Response.serverError()
                    .entity("\u274c Error al generar el backup.")
                    .type(MediaType.TEXT_PLAIN + ";charset=UTF-8")
                    .build();
        }
    }

    @POST
    @Path("/upload")
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@FormParam("file") FileUpload file) {
        String redirect = "/private/admin/backup";
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (file == null || file.size() == 0) {
            LOG.warn("No file uploaded for restore");
            return Response.seeOther(UriBuilder.fromPath(redirect)
                    .queryParam("msg", "\u274c Archivo inv\u00e1lido.")
                    .build()).build();
        }
        String fileName = file.fileName();
        if (fileName == null || !fileName.endsWith(".zip")) {
            LOG.warnf("Invalid backup type: %s", fileName);
            return Response.seeOther(UriBuilder.fromPath(redirect)
                    .queryParam("msg", "\u274c Archivo no es ZIP.")
                    .build()).build();
        }
        if (!fileName.matches("backup_.*_v" + java.util.regex.Pattern.quote(appVersion) + "\\.zip")) {
            LOG.warnf("Incompatible backup version: %s", fileName);
            return Response.seeOther(UriBuilder.fromPath(redirect)
                    .queryParam("msg", "\u274c Versi\u00f3n incompatible.")
                    .build()).build();
        }
        java.nio.file.Path dataDir = Paths.get(System.getProperty("eventflow.data.dir", "data"));
        try {
            Files.createDirectories(dataDir);
            long free = dataDir.toFile().getUsableSpace();
            long size = file.size();
            if (free < size) {
                LOG.warn("Not enough disk space for restore");
                return Response.seeOther(UriBuilder.fromPath(redirect)
                        .queryParam("msg", "\u274c Espacio insuficiente.")
                        .build()).build();
            }
            try (InputStream in = Files.newInputStream(file.filePath());
                 ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    java.nio.file.Path target = dataDir.resolve(entry.getName());
                    Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    zis.closeEntry();
                }
            }
            persistence.flush();
            eventService.reload();
            speakerService.reload();
            LOG.infof("Backup restored from %s", fileName);
            return Response.seeOther(UriBuilder.fromPath(redirect)
                    .queryParam("msg", "\u2705 Backup restaurado.")
                    .build()).build();
        } catch (Exception e) {
            LOG.error("Failed to restore backup", e);
            return Response.seeOther(UriBuilder.fromPath(redirect)
                    .queryParam("msg", "\u274c Error al restaurar.")
                    .build()).build();
        }
    }
}
