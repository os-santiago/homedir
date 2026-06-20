package com.scanales.homedir.trending;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class TrendingServiceTest {

  @Inject
  TrendingService trendingService;

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
}
