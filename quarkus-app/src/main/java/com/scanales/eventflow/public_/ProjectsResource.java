package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.UsageMetricsService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/proyectos")
public class ProjectsResource {

  @Inject UsageMetricsService metrics;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance proyectos(List<ProjectCard> projects);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance proyectos(
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/proyectos", headers, context);
    List<ProjectCard> projects =
        List.of(
            new ProjectCard(
                "Homedir Core",
                "Base de la plataforma: autenticación, perfiles y orquestación de módulos.",
                "En producción",
                "https://github.com/os-santiago/homedir",
                "Backend"),
            new ProjectCard(
                "Notificaciones Globales",
                "Canal WebSocket y centro de notificaciones para eventos y alertas.",
                "Beta",
                "https://github.com/os-santiago/homedir/tree/main/quarkus-app",
                "Tiempo real"),
            new ProjectCard(
                "Comunidad",
                "Directorio de integrantes y onboarding con GitHub para contribuir.",
                "En diseño",
                "/comunidad",
                "Personas"));
    return Templates.proyectos(projects);
  }

  public record ProjectCard(
      String name, String description, String status, String link, String tag) {}
}
