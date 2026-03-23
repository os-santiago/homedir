package com.scanales.homedir.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.homedir.reputation.ReputationRecognitionService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Path("/api/community/reputation/recognitions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReputationRecognitionApiResource {

  @Inject SecurityIdentity identity;
  @Inject ReputationRecognitionService reputationRecognitionService;

  @POST
  @Authenticated
  public Response createRecognition(RecognitionRequest request) {
    Optional<String> validatorUserId = currentUserId();
    if (validatorUserId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "recognition_invalid_payload"))
          .build();
    }
    ReputationRecognitionService.RecognitionResult result =
        reputationRecognitionService.recognize(
            validatorUserId.get(),
            request.targetUserId(),
            request.sourceObjectType(),
            request.sourceObjectId(),
            request.recognitionType());

    if (result.disabled()) {
      return Response.status(Response.Status.CONFLICT).entity(Map.of("error", result.reason())).build();
    }
    if (result.accepted()) {
      return Response.ok(
              new RecognitionResponse(
                  "ok", result.recognitionType(), result.eventType(), request.sourceObjectId()))
          .build();
    }
    if (result.rateLimited()) {
      return Response.status(429).entity(Map.of("error", result.reason())).build();
    }
    return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", result.reason())).build();
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
    if (principal == null || principal.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(principal.toLowerCase(Locale.ROOT));
  }

  public record RecognitionRequest(
      @JsonProperty("target_user_id") String targetUserId,
      @JsonProperty("source_object_type") String sourceObjectType,
      @JsonProperty("source_object_id") String sourceObjectId,
      @JsonProperty("recognition_type") String recognitionType) {}

  public record RecognitionResponse(
      String status,
      @JsonProperty("recognition_type") String recognitionType,
      @JsonProperty("event_type") String eventType,
      @JsonProperty("source_object_id") String sourceObjectId) {}
}
