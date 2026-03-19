package com.scanales.homedir.public_;

import com.scanales.homedir.service.UsageMetricsService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Redirects CTA clicks and records metrics. */
@Path("/cta")
public class CtaResource {

  @Inject UsageMetricsService metrics;

  @ConfigProperty(
      name = "links.releases-url",
      defaultValue = "https://github.com/os-santiago/homedir/releases")
  String releasesUrl;

  @ConfigProperty(
      name = "links.issues-url",
      defaultValue = "https://github.com/os-santiago/homedir/issues")
  String issuesUrl;

  @ConfigProperty(name = "links.donate-url", defaultValue = "https://ko-fi.com/sergiocanales")
  String donateUrl;

  @GET
  @Path("/{type}")
  @PermitAll
  public Response redirect(@PathParam("type") String type) {
    String target;
    switch (type) {
      case "releases" -> target = releasesUrl;
      case "issues" -> target = issuesUrl;
      case "kofi" -> target = donateUrl;
      default -> {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
    }
    metrics.recordCta(type, null);
    return Response.temporaryRedirect(UriBuilder.fromUri(target).build()).build();
  }
}
