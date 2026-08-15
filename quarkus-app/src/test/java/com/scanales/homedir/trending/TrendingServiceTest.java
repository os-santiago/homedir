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
  public void testParseHtmlExtractsStarsToday() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    TrendingRepo react = repos.get(0);
    assertEquals(1234, react.starsToday());
    assertEquals(45678, react.stars(), "total stars should differ from stars today");

    TrendingRepo goname =
        repos.stream().filter(r -> r.name().equals("goname")).findFirst().orElseThrow();
    assertEquals(3, goname.starsToday());
  }

  @Test
  public void testParseHtmlExtractsForks() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    TrendingRepo react = repos.get(0);
    assertEquals(12345, react.forks());

    TrendingRepo hdl =
        repos.stream().filter(r -> r.name().equals("hdl-lang")).findFirst().orElseThrow();
    assertEquals(34, hdl.forks());
  }

  @Test
  public void testParseHtmlExtractsContributors() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    TrendingRepo react = repos.get(0);
    assertEquals(1001, react.contributors());

    TrendingRepo ruff = repos.stream().filter(r -> r.name().equals("ruff")).findFirst().orElseThrow();
    assertEquals(89, ruff.contributors());
  }

  @Test
  public void testParseHtmlFillsDescriptionEsFromCatalog() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    TrendingRepo react = repos.get(0);
    assertEquals(
        "Biblioteca declarativa, eficiente y flexible de JavaScript para construir interfaces de usuario.",
        react.descriptionEs());

    TrendingRepo ruff = repos.stream().filter(r -> r.name().equals("ruff")).findFirst().orElseThrow();
    assertEquals("Linter y formateador de Python extremadamente rápido, escrito en Rust.", ruff.descriptionEs());
  }

  @Test
  public void testParseHtmlDescriptionEsFallsBackToNull() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    TrendingRepo goname =
        repos.stream().filter(r -> r.name().equals("goname")).findFirst().orElseThrow();
    assertNull(goname.descriptionEs(), "unknown repos should have null descriptionEs");
  }

  @Test
  public void testFilterReposByLanguage() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    List<TrendingRepo> rust = trendingService.filterRepos(repos, "Rust", null, null);
    assertEquals(1, rust.size());
    assertEquals("ruff", rust.get(0).name());

    List<TrendingRepo> none = trendingService.filterRepos(repos, "Kotlin", null, null);
    assertTrue(none.isEmpty());
  }

  @Test
  public void testFilterReposByMinStars() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    List<TrendingRepo> big = trendingService.filterRepos(repos, null, 1000, null);
    assertEquals(3, big.size(), "react, ruff, langchain have >= 1000 stars");
    assertTrue(big.stream().allMatch(r -> r.stars() >= 1000));

    List<TrendingRepo> huge = trendingService.filterRepos(repos, null, 100000, null);
    assertTrue(huge.isEmpty());
  }

  @Test
  public void testFilterReposByQuery() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    List<TrendingRepo> byName = trendingService.filterRepos(repos, null, null, "react");
    assertEquals(1, byName.size());
    assertEquals("react", byName.get(0).name());

    List<TrendingRepo> byOwner = trendingService.filterRepos(repos, null, null, "astral");
    assertEquals(1, byOwner.size());
    assertEquals("ruff", byOwner.get(0).name());

    List<TrendingRepo> combined = trendingService.filterRepos(repos, null, null, "langchain");
    assertEquals(1, combined.size());
  }

  @Test
  public void testFilterReposNullInputs() {
    assertNull(trendingService.filterRepos(null, null, null, null));
    assertTrue(trendingService.filterRepos(List.of(), null, null, null).isEmpty());

    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);
    assertEquals(repos.size(), trendingService.filterRepos(repos, null, null, null).size());
    assertEquals(repos.size(), trendingService.filterRepos(repos, "", null, "").size());
  }

  @Test
  public void testExtractLanguages() {
    List<TrendingRepo> repos = trendingService.parseHtml(fixtureHtml);

    List<String> languages = trendingService.extractLanguages(repos);
    assertEquals(List.of("Go", "JavaScript", "Python", "Rust"), languages);
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
