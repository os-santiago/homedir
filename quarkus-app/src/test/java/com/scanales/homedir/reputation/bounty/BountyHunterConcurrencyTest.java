package com.scanales.homedir.reputation.bounty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    try {
      List<Callable<BountyHunterScore>> tasks = new ArrayList<>();
      
      for (int i = 0; i < threads; i++) {
        tasks.add(() -> service.awardIssueCreationPoints(userId, issueNumber, label, "admin"));
      }
      
      List<Future<BountyHunterScore>> results = executor.invokeAll(tasks);
      
      for (Future<BountyHunterScore> result : results) {
        result.get();
      }
      
      BountyHunterScore finalScore = service.getScoreForUser(userId).get();
      // Only one award should happen, so total points should be 50
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
    try {
      List<Callable<BountyHunterScore>> tasks = new ArrayList<>();
      
      for (int i = 0; i < threads; i++) {
        tasks.add(() -> service.awardIssueResolutionPoints(userId, issueNumber, prNumber, label));
      }
      
      List<Future<BountyHunterScore>> results = executor.invokeAll(tasks);
      
      for (Future<BountyHunterScore> result : results) {
        result.get();
      }
      
      BountyHunterScore finalScore = service.getScoreForUser(userId).get();
      // Only one award should happen, so total points should be 100
      assertEquals(100L, finalScore.totalPoints());
    } finally {
      executor.shutdownNow();
    }
  }
}
