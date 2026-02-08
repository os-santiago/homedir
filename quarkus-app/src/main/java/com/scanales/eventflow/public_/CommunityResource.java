package com.scanales.eventflow.public_;

import com.scanales.eventflow.community.CommunityBoardService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/comunidad")
public class CommunityResource {
  private static final Logger LOG = Logger.getLogger(CommunityResource.class);

  @Inject SecurityIdentity identity;
  @Inject CommunityBoardService boardService;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance community(
        boolean isAuthenticated, String initialView, int homedirUsers, int githubUsers, int discordUsers);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance view(@QueryParam("view") String viewParam) {
    boolean authenticated = isAuthenticated();
    String initialView = normalizeView(viewParam);
    var summary = boardService.summary();
    TemplateInstance template =
        Templates.community(
            authenticated,
            initialView,
            summary.homedirUsers(),
            summary.githubUsers(),
            summary.discordUsers());
    return template
        .data("activePage", "comunidad")
        .data("activeCommunitySubmenu", "new".equals(initialView) ? "feed" : "picks")
        .data("userAuthenticated", authenticated)
        .data("userName", currentUserName().orElse(null))
        .data("userInitial", initialFrom(currentUserName().orElse(null)));
  }

  @GET
  @Path("/feed")
  @PermitAll
  public Response feed() {
    return Response.seeOther(URI.create("/comunidad?view=new")).build();
  }

  @GET
  @Path("/picks")
  @PermitAll
  public Response picks() {
    return Response.seeOther(URI.create("/comunidad?view=featured")).build();
  }

  private String normalizeView(String viewParam) {
    if (viewParam == null || viewParam.isBlank()) {
      return "featured";
    }
    String normalized = viewParam.trim().toLowerCase();
    if ("featured".equals(normalized) || "new".equals(normalized)) {
      return normalized;
    }
    return "featured";
  }

  private Optional<String> currentUserName() {
    if (!isAuthenticated()) {
      return Optional.empty();
    }
    String name = identity.getAttribute("name");
    if (name == null || name.isBlank()) {
      name = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    }
    return Optional.ofNullable(name);
  }

  private boolean isAuthenticated() {
    try {
      return identity != null && !identity.isAnonymous();
    } catch (Exception e) {
      LOG.warn("Security identity check failed (treating as anonymous): " + e.getMessage());
      return false;
    }
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
