package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class GithubServiceCacheTest {

  @Test
  void keepsContributorsInMemoryBetweenRequests() {
    StubGithubService service = newService();
    service.enqueue(
        List.of(new GithubService.GithubContributor("alice", "https://avatar", "https://profile", 5)));

    service.refreshHomeProjectContributorsNowForTests();
    List<GithubService.GithubContributor> first = service.fetchHomeProjectContributors();
    List<GithubService.GithubContributor> second = service.fetchHomeProjectContributors();

    assertEquals(1, service.fetchCalls);
    assertEquals(1, first.size());
    assertSame(first, second);
    assertEquals("os-santiago", service.lastOwner);
    assertEquals("homedir", service.lastRepo);
  }

  @Test
  void keepsLastSuccessfulSnapshotWhenRefreshReturnsEmpty() {
    StubGithubService service = newService();
    service.enqueue(
        List.of(new GithubService.GithubContributor("alice", "https://avatar", "https://profile", 5)));
    service.refreshHomeProjectContributorsNowForTests();

    service.enqueue(List.of());
    service.refreshHomeProjectContributorsNowForTests();
    List<GithubService.GithubContributor> contributors = service.fetchHomeProjectContributors();

    assertEquals(2, service.fetchCalls);
    assertFalse(contributors.isEmpty());
    assertEquals("alice", contributors.getFirst().login());
  }

  private StubGithubService newService() {
    StubGithubService service = new StubGithubService();
    service.objectMapper = new ObjectMapper();
    service.config = mockConfig();
    service.homeProjectRepoOwner = "os-santiago";
    service.homeProjectRepoName = "homedir";
    service.contributorsCacheTtl = Duration.ofHours(24);
    return service;
  }

  private Config mockConfig() {
    Config cfg = Mockito.mock(Config.class);
    when(cfg.getOptionalValue(eq("GH_TOKEN"), eq(String.class))).thenReturn(Optional.empty());
    when(cfg.getOptionalValue(eq("GH_CLIENT_ID"), eq(String.class))).thenReturn(Optional.empty());
    when(cfg.getOptionalValue(eq("GH_CLIENT_SECRET"), eq(String.class))).thenReturn(Optional.empty());
    return cfg;
  }

  static class StubGithubService extends GithubService {
    final ArrayDeque<List<GithubService.GithubContributor>> queuedResponses = new ArrayDeque<>();
    int fetchCalls = 0;
    String lastOwner = null;
    String lastRepo = null;

    @Override
    List<GithubService.GithubContributor> loadContributorsFromGithub(String owner, String repo) {
      fetchCalls++;
      lastOwner = owner;
      lastRepo = repo;
      if (queuedResponses.isEmpty()) {
        return List.of();
      }
      return queuedResponses.removeFirst();
    }

    void enqueue(List<GithubService.GithubContributor> contributors) {
      queuedResponses.addLast(contributors);
    }
  }
}
