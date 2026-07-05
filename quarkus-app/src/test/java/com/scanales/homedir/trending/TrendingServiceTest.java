package com.scanales.homedir.trending;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TrendingServiceTest {

  @Inject TrendingService trendingService;

  private String fixtureHtml;

  @BeforeEach
  void loadFixture() throws IOException, URISyntaxException {
    URI uri =
        Objects.requireNonNull(
                getClass()
                    .getClassLoader()
                    .getResource("fixtures/trending/github-trending-daily.html"))
            .toURI();
    fixtureHtml = Files.readString(Path.of(uri));
  }

  @Test
  public void testGetTrendingDaily() {
    List<TrendingRepo> repos = trendingService.getTrending(TrendingPeriod.DAILY, 3);
    assertNotNull(repos);
    assertTrue(repos.size() <= 3);
  }

  @Test
  public void testGetTrendingWeekly() {
    List<TrendingRepo> repos = trendingService.getTrending(TrendingPeriod.WEEKLY, 5);
    assertNotNull(repos);
    assertTrue(repos.size() <= 5);
  }

  @Test
  public void testGetTrendingMonthly() {
    List<TrendingRepo> repos = trendingService.getTrending(TrendingPeriod.MONTHLY, 10);
    assertNotNull(repos);
    assertTrue(repos.size() <= 10);
  }

  @Test
  public void testCountLimiting() {
    List<TrendingRepo> repos1 = trendingService.getTrending(TrendingPeriod.DAILY, 1);
    assertTrue(repos1.size() <= 1);

    List<TrendingRepo> repos10 = trendingService.getTrending(TrendingPeriod.DAILY, 10);
    assertTrue(repos10.size() <= 10);

    List<TrendingRepo> reposMax = trendingService.getTrending(TrendingPeriod.DAILY, 100);
    assertTrue(reposMax.size() <= 10);
  }

  @Test
  public void testPeriodParsing() {
    assertEquals(TrendingPeriod.DAILY, TrendingPeriod.fromString("daily"));
    assertEquals(TrendingPeriod.WEEKLY, TrendingPeriod.fromString("weekly"));
    assertEquals(TrendingPeriod.MONTHLY, TrendingPeriod.fromString("monthly"));
    assertEquals(TrendingPeriod.DAILY, TrendingPeriod.fromString(null));
    assertEquals(TrendingPeriod.DAILY, TrendingPeriod.fromString("invalid"));
  }

  @Test
  public void testParseHtmlReturnsRepos() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);
    assertEquals(5, repos.size(), "should parse all 5 Box-row articles");
  }

  @Test
  public void testParseHtmlExtractsFields() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    TrendingRepo first = repos.get(0);
    assertEquals("react", first.name());
    assertEquals("facebook", first.owner());
    assertEquals(
        "A declarative, efficient, and flexible JavaScript library for building user interfaces.",
        first.description());
    assertEquals(45678, first.stars());
    assertEquals("JavaScript", first.language());
    assertEquals("https://github.com/facebook/react", first.url());
  }

  @Test
  public void testParseHtmlHandlesMissingLanguage() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    // "hdl-lang" article has no itemprop="programmingLanguage" span
    TrendingRepo hdl =
        repos.stream().filter(r -> r.name().equals("hdl-lang")).findFirst().orElseThrow();
    assertEquals("hdl", hdl.owner());
    assertEquals("", hdl.language(), "should default to empty string when no language span");
  }

  @Test
  public void testParseHtmlHandlesMissingDescription() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    // "goname" article has no description p tag
    TrendingRepo goname =
        repos.stream().filter(r -> r.name().equals("goname")).findFirst().orElseThrow();
    assertEquals("", goname.description(), "should default to empty string when no description");
    assertEquals("Go", goname.language());
  }

  @Test
  public void testParseHtmlHandlesBoxRowWithCompoundClass() {
    // Add a compound class variant to verify regex handles Box-row--focus-gray etc.
    String compound =
        fixtureHtml.replace(
            "<article class=\"Box-row\">", "<article class=\"Box-row Box-row--focus-gray\">");
    List<TrendingRepo> repos = trendingService.parseHtml(compound);
    assertEquals(5, repos.size(), "should parse Box-row with compound class");
  }

  @Test
  public void testParseHtmlEmptyInput() {
    List<TrendingRepo> repos = trendingService.parseHtml("");
    assertTrue(repos.isEmpty());

    repos = trendingService.parseHtml("<html><body>no Box-row articles here</body></html>");
    assertTrue(repos.isEmpty());
  }
}
