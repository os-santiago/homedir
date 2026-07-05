package com.scanales.homedir.trending;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests that TrendingCacheSnapshot serializes and deserializes correctly via Jackson (same
 * ObjectMapper used by TrendingService for disk persistence).
 */
@QuarkusTest
public class TrendingCachePersistenceTest {

  @Inject ObjectMapper mapper;

  @Test
  public void testCacheSnapshotRoundTrip() throws Exception {
    List<TrendingRepo> repos =
        List.of(
            new TrendingRepo(
                "react",
                "facebook",
                "A JS library",
                45678,
                "JavaScript",
                "https://github.com/facebook/react"),
            new TrendingRepo(
                "ruff",
                "astral-sh",
                "Fast Python linter",
                1234,
                "Rust",
                "https://github.com/astral-sh/ruff"));

    Instant now = Instant.now();
    TrendingCacheSnapshot original =
        new TrendingCacheSnapshot(repos, now, now, TrendingPeriod.DAILY);

    String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(original);
    assertNotNull(json);
    assertTrue(json.contains("facebook"), "JSON should contain repo owner");
    assertTrue(json.contains("DAILY"), "JSON should contain period");

    TrendingCacheSnapshot restored = mapper.readValue(json, TrendingCacheSnapshot.class);
    assertNotNull(restored);
    assertEquals(2, restored.repos().size());
    assertEquals("react", restored.repos().get(0).name());
    assertEquals("facebook", restored.repos().get(0).owner());
    assertEquals(45678, restored.repos().get(0).stars());
    assertEquals("JavaScript", restored.repos().get(0).language());
    assertEquals("https://github.com/facebook/react", restored.repos().get(0).url());
    assertEquals(TrendingPeriod.DAILY, restored.period());
    assertNotNull(restored.lastRefreshTime());
    assertNotNull(restored.lastSuccessTime());
  }

  @Test
  public void testCacheSnapshotRoundTripWithDescriptionEs() throws Exception {
    TrendingRepo repo =
        new TrendingRepo(
            "react",
            "facebook",
            "A JS library",
            45678,
            "JavaScript",
            "https://github.com/facebook/react",
            "Una librer\u00eda JS");
    TrendingCacheSnapshot original =
        new TrendingCacheSnapshot(
            List.of(repo), Instant.now(), Instant.now(), TrendingPeriod.WEEKLY);

    String json = mapper.writeValueAsString(original);
    TrendingCacheSnapshot restored = mapper.readValue(json, TrendingCacheSnapshot.class);

    assertEquals(
        "Una librer\u00eda JS",
        restored.repos().get(0).descriptionEs(),
        "descriptionEs should survive JSON round-trip");
    assertEquals(TrendingPeriod.WEEKLY, restored.period());
  }

  @Test
  public void testCacheSnapshotStaleness() {
    Instant oldTime = Instant.now().minusSeconds(200_000); // ~2.3 days ago
    TrendingCacheSnapshot snapshot =
        new TrendingCacheSnapshot(List.of(), oldTime, oldTime, TrendingPeriod.DAILY);

    assertTrue(
        snapshot.isStale(Duration.ofHours(48)),
        "Snapshot from 2.3 days ago should be stale with 48h TTL");
    assertFalse(
        snapshot.isStale(Duration.ofHours(72)),
        "Snapshot from 2.3 days ago should not be stale with 72h TTL");
  }

  @Test
  public void testCacheSnapshotEmpty() {
    TrendingCacheSnapshot empty = TrendingCacheSnapshot.empty(TrendingPeriod.MONTHLY);
    assertTrue(empty.repos().isEmpty());
    assertNull(empty.lastRefreshTime());
    assertNull(empty.lastSuccessTime());
    assertEquals(TrendingPeriod.MONTHLY, empty.period());
    assertTrue(empty.isStale(Duration.ofMinutes(1)));
  }
}
