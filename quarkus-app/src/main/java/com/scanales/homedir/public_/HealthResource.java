package com.scanales.homedir.public_;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

@Path("/health")
@ApplicationScoped
public class HealthResource {

  @Inject @Liveness Instance<HealthCheck> livenessChecks;

  @Inject @Readiness Instance<HealthCheck> readinessChecks;

  @GET
  @PermitAll
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    return aggregate("liveness", getChecks(livenessChecks));
  }

  @GET
  @Path("/ready")
  @PermitAll
  @Produces(MediaType.APPLICATION_JSON)
  public Response ready() {
    return aggregate("readiness", getChecks(readinessChecks));
  }

  private Response aggregate(String type, List<HealthCheckResponse> checks) {
    boolean allUp = checks.stream().allMatch(c -> c.getStatus() == Status.UP);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", allUp ? "UP" : "DOWN");
    result.put("checks", checks.stream().map(this::toMap).collect(Collectors.toList()));
    int statusCode = allUp ? 200 : 503;
    return Response.status(statusCode).entity(result).build();
  }

  private Map<String, Object> toMap(HealthCheckResponse check) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("name", check.getName());
    map.put("status", check.getStatus() == Status.UP ? "UP" : "DOWN");
    check
        .getData()
        .ifPresent(
            data -> {
              Map<String, Object> dataMap = new LinkedHashMap<>();
              data.forEach((k, v) -> dataMap.put(k, v));
              if (!dataMap.isEmpty()) {
                map.put("data", dataMap);
              }
            });
    return map;
  }

  private List<HealthCheckResponse> getChecks(Instance<HealthCheck> checks) {
    List<HealthCheckResponse> results = new ArrayList<>();
    for (HealthCheck check : checks) {
      try {
        results.add(check.call());
      } catch (Exception e) {
        results.add(
            HealthCheckResponse.named(check.getClass().getSimpleName())
                .down()
                .withData("error", e.getMessage())
                .build());
      }
    }
    return results;
  }
}
