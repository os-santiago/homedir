package com.scanales.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance community(boolean isAuthenticated, String initialView);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance view(@QueryParam("view") String view) {
    TemplateInstance template = Templates.community(isAuthenticated(), normalizeView(view));
    return template
        .data("activePage", "comunidad")
        .data("userAuthenticated", isAuthenticated())
        .data("userName", currentUserName().orElse(null))
        .data("userInitial", initialFrom(currentUserName().orElse(null)));
  }

  @GET
  @Path("/feed")
  public Response feedAlias() {
    return Response.seeOther(URI.create("/community/feed")).build();
  }

  @GET
  @Path("/picks")
  public Response picksAlias() {
    return Response.seeOther(URI.create("/community/picks")).build();
  }

  @GET
  @Path("/board")
  public Response boardAlias() {
    return Response.seeOther(URI.create("/community/board")).build();
  }

  @GET
  @Path("/board/{group}")
  public Response boardGroupAlias(@PathParam("group") String group) {
    return Response.seeOther(URI.create("/community/board/" + group)).build();
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

  private static String normalizeView(String raw) {
    if (raw == null || raw.isBlank()) {
      return "featured";
    }
    String normalized = raw.trim().toLowerCase();
    if ("new".equals(normalized) || "featured".equals(normalized)) {
      return normalized;
    }
    return "featured";
  }
}
