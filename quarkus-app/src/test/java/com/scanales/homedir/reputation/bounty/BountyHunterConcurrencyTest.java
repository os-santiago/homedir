package com.scanales.homedir.reputation.bounty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BountyHunterConcurrencyTest {

  @Inject BountyHunterService service;
  
  @InjectMock BountyHunterConfigService configService;
  
  @Test
  void testConcurrentIssueCreationPoints() throws Exception {
    String userId = "concurrentUser";
    String issueNumber = "100";
    String label = "bug";
    
    when(configService.getPointsForLabel(anyString())).thenReturn(50L);
    when(configService.isAdminUser(anyString())).thenReturn(true);
    
    int threads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch readyLatch = new CountDownLatch(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    try {
      List<Future<BountyHunterScore>> results = new ArrayList<>();
      
      for (int i = 0; i < threads; i++) {
        results.add(executor.submit(() -> {
          readyLatch.countDown();
          assertTrue(startLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for start");
          return service.awardIssueCreationPoints(userId, issueNumber, label, "admin");
        }));
      }
      
      assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for workers to be ready");
      startLatch.countDown();
      
      for (Future<BountyHunterScore> result : results) {
        result.get(5, TimeUnit.SECONDS);
      }
      
      BountyHunterScore finalScore = service.getScoreForUser(userId).get();
      assertEquals(50L, finalScore.totalPoints());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void testConcurrentIssueResolutionPoints() throws Exception {
    String userId = "concurrentUser2";
    String issueNumber = "101";
    String prNumber = "200";
    String label = "bug";
    
    when(configService.getPointsForLabel(anyString())).thenReturn(100L);
    
    int threads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch readyLatch = new CountDownLatch(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    try {
      List<Future<BountyHunterScore>> results = new ArrayList<>();
      
      for (int i = 0; i < threads; i++) {
        results.add(executor.submit(() -> {
          readyLatch.countDown();
          assertTrue(startLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for start");
          return service.awardIssueResolutionPoints(userId, issueNumber, prNumber, label);
        }));
      }
      
      assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for workers to be ready");
      startLatch.countDown();
      
      for (Future<BountyHunterScore> result : results) {
        result.get(5, TimeUnit.SECONDS);
      }
      
      BountyHunterScore finalScore = service.getScoreForUser(userId).get();
      assertEquals(100L, finalScore.totalPoints());
    } finally {
      executor.shutdownNow();
    }
  }
}
