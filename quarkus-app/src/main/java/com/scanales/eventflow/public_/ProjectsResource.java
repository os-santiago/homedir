package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.UsageMetricsService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/proyectos")
public class ProjectsResource {

  @Inject
  UsageMetricsService metrics;
  @Inject
  SecurityIdentity identity;
  @Inject
  com.fasterxml.jackson.databind.ObjectMapper mapper;
  @jakarta.inject.Inject
  org.eclipse.microprofile.config.Config config;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance proyectos(List<ProjectCard> projects);
  }

  private final java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().build();
  private final java.util.concurrent.atomic.AtomicReference<List<ProjectCard>> cache = new java.util.concurrent.atomic.AtomicReference<>();
  private volatile java.time.Instant lastFetch = java.time.Instant.MIN;

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance proyectos(
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/proyectos", headers, context);
    List<ProjectCard> projects = getProjects();
    TemplateInstance template = Templates.proyectos(projects);
    return withLayoutData(template, "proyectos");
  }

  private List<ProjectCard> getProjects() {
    if (java.time.Instant.now().isBefore(lastFetch.plusSeconds(3600)) && cache.get() != null) {
      return cache.get();
    }
    try {
      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
          .uri(java.net.URI.create("https://api.github.com/orgs/os-santiago/repos?sort=updated&per_page=100"))
          .header("Accept", "application/vnd.github+json")
          .GET()
          .build();

      // Add token if available to avoid rate limits
      String token = config.getOptionalValue("GH_TOKEN", String.class).orElse("");
      if (!token.isBlank()) {
        request = java.net.http.HttpRequest.newBuilder(request.uri())
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build();
      }

      var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
        List<ProjectCard> list = new java.util.ArrayList<>();
        if (root.isArray()) {
          for (com.fasterxml.jackson.databind.JsonNode node : root) {
            if (node.path("archived").asBoolean(false))
              continue;
            String name = node.path("name").asText();
            String desc = node.path("description").asText("Sin oscripción");
            String url = node.path("html_url").asText();
            String lang = node.path("language").asText("General");
            list.add(new ProjectCard(name, desc, "Activo", url, lang));
          }
        }
        // Custom additions or overrides could go here
        list.add(new ProjectCard("Comunidad", "Directorio de integrantes.", "En diseño", "/comunidad", "Personas"));

        cache.set(list);
        lastFetch = java.time.Instant.now();
        return list;
      }
    } catch (Exception e) {
      // Log error and return cached or empty
      e.printStackTrace();
    }
    return cache.get() != null ? cache.get() : List.of();
  }

  public record ProjectCard(
      String name, String description, String status, String link, String tag) {
  }

  private TemplateInstance withLayoutData(TemplateInstance templateInstance, String activePage) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : null;
    return templateInstance
        .data("activePage", activePage)
        .data("userAuthenticated", authenticated)
        .data("userName", userName)
        .data("userInitial", initialFrom(userName));
  }

  private String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase();
  }
}
