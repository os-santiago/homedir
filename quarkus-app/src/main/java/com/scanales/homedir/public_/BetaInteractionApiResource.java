package com.scanales.homedir.public_;

import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Optional;

@Path("/api/beta")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BetaInteractionApiResource {

  @Inject UsageMetricsService metrics;
  @Inject SecurityIdentity identity;
  @Inject GamificationService gamificationService;

  @POST
  @Path("/interaction")
  @PermitAll
  public Response track(BetaInteractionRequest body) {
    if (body == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"invalid_body\"}").build();
    }
    String event = normalizeEvent(body.event());
    String zone = normalizeZone(body.zone());
    if (event == null || zone == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"invalid_input\"}").build();
    }

    metrics.recordFunnelStep("beta." + event + "." + zone);

    if ("open".equals(event) || "visit".equals(event)) {
      currentUserId().ifPresent(
          userId -> {
            switch (zone) {
              case "inn" -> gamificationService.award(userId, GamificationActivity.HOME_VIEW);
              case "guild" -> gamificationService.award(userId, GamificationActivity.COMMUNITY_PICKS_VIEW);
              case "theater" -> gamificationService.award(userId, GamificationActivity.EVENT_DIRECTORY_VIEW);
              case "cityhall" -> gamificationService.award(userId, GamificationActivity.PROJECT_VIEW);
              default -> {
              }
            }
          });
    }

    return Response.accepted().entity("{\"ok\":true}").build();
  }

  private Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal != null && !principal.isBlank()) {
      return Optional.of(principal.toLowerCase(Locale.ROOT));
    }
    return Optional.empty();
  }

  private String normalizeEvent(String raw) {
    if (raw == null) {
      return null;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "open", "visit", "preview" -> raw.trim().toLowerCase(Locale.ROOT);
      default -> null;
    };
  }

  private String normalizeZone(String raw) {
    if (raw == null) {
      return null;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "inn", "guild", "theater", "cityhall" -> raw.trim().toLowerCase(Locale.ROOT);
      default -> null;
    };
  }

  public record BetaInteractionRequest(String event, String zone) {}
}
