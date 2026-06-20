package com.scanales.homedir.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Readiness
@ApplicationScoped
public class ExternalApiHealthCheck implements HealthCheck {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final String GITHUB_API_URL = "https://api.github.com";
  private static final String GITHUB_REPO_URL = "https://api.github.com/repos/os-santiago/homedir";

  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(TIMEOUT)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  @Override
  public HealthCheckResponse call() {
    boolean githubOk = checkEndpoint(GITHUB_API_URL, "github-api");
    boolean repoOk = checkEndpoint(GITHUB_REPO_URL, "github-repo");

    boolean allOk = githubOk && repoOk;

    var builder = HealthCheckResponse.named("external-apis")
        .status(allOk)
        .withData("githubApi", githubOk ? "reachable" : "unreachable")
        .withData("homedirRepo", repoOk ? "reachable" : "unreachable");

    if (!githubOk) {
      builder.withData("githubApiUrl", GITHUB_API_URL);
    }
    if (!repoOk) {
      builder.withData("homedirRepoUrl", GITHUB_REPO_URL);
    }

    return builder.build();
  }

  private boolean checkEndpoint(String url, String name) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(TIMEOUT)
          .header("User-Agent", "Homedir-HealthCheck")
          .GET()
          .build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() < 500;
    } catch (Exception e) {
      return false;
    }
  }
}
