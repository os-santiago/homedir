package com.scanales.homedir.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Liveness
@ApplicationScoped
public class OidcHealthCheck implements HealthCheck {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final String OIDC_DISCOVERY_URL = "https://accounts.google.com/.well-known/openid-configuration";

  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(TIMEOUT)
      .build();

  @Override
  public HealthCheckResponse call() {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(OIDC_DISCOVERY_URL))
          .timeout(TIMEOUT)
          .GET()
          .build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      boolean reachable = response.statusCode() == 200;
      return HealthCheckResponse.named("oidc-provider")
          .status(reachable)
          .withData("url", OIDC_DISCOVERY_URL)
          .withData("statusCode", response.statusCode())
          .build();
    } catch (Exception e) {
      return HealthCheckResponse.named("oidc-provider")
          .down()
          .withData("error", e.getClass().getSimpleName() + ": " + e.getMessage())
          .build();
    }
  }
}
