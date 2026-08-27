package com.scanales.homedir.reputation.bounty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BountyHunterServiceTest {

  @Inject BountyHunterService service;
  @InjectMock BountyHunterRepository repository;
  @InjectMock BountyHunterConfigService configService;

  @BeforeEach
  void setUp() {
    reset(repository, configService);
  }

  @Test
  void awardIssueCreationPoints_validLabel_awardsPoints() {
    String userId = "testuser";
    String issueNumber = "123";
    String label = "bug-impact-medium";
    String validatedBy = "admin";
    long points = 15L;

    when(configService.getPointsForLabel(label)).thenReturn(points);
    when(configService.isAdminUser(validatedBy)).thenReturn(true);
    when(repository.awardAtomically(eq(userId), eq(issueNumber), eq(BountyHunterEventType.ISSUE_LABEL_APPROVED), any(), any()))
        .thenAnswer(invocation -> {
          java.util.function.Function<Optional<BountyHunterScore>, BountyHunterScore> updater = invocation.getArgument(3);
          return updater.apply(Optional.empty());
        });

    BountyHunterScore result =
        service.awardIssueCreationPoints(userId, issueNumber, label, validatedBy);

    assertNotNull(result);
    assertEquals(userId, result.userId());
    assertEquals(points, result.totalPoints());
    assertEquals(points, result.issueCreationPoints());
    assertEquals(0L, result.issueResolutionPoints());
    assertEquals(1, result.issuesCreatedCount());
    assertEquals(0, result.issuesResolvedCount());
  }

  @Test
  void awardIssueCreationPoints_invalidLabel_throwsException() {
    when(configService.getPointsForLabel("invalid")).thenReturn(0L);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.awardIssueCreationPoints("user", "123", "invalid", "admin"));
  }

  @Test
  void awardIssueCreationPoints_nonAdminValidator_throwsException() {
    when(configService.getPointsForLabel("bug-impact-low")).thenReturn(5L);
    when(configService.isAdminUser("regular")).thenReturn(false);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.awardIssueCreationPoints("user", "123", "bug-impact-low", "regular"));
  }

  @Test
  void awardIssueResolutionPoints_validLabel_awardsPoints() {
    String userId = "testuser";
    long points = 20L;
    when(configService.getPointsForLabel("feature-request")).thenReturn(points);
    when(repository.awardAtomically(eq(userId), eq("127"), eq(BountyHunterEventType.ISSUE_RESOLVED_BY_PR), any(), any()))
        .thenAnswer(invocation -> {
          java.util.function.Function<Optional<BountyHunterScore>, BountyHunterScore> updater = invocation.getArgument(3);
          return updater.apply(Optional.empty());
        });

    BountyHunterScore result =
        service.awardIssueResolutionPoints(userId, "127", "45", "feature-request");

    assertEquals(points, result.totalPoints());
    assertEquals(points, result.issueResolutionPoints());
    assertEquals(1, result.issuesResolvedCount());
  }

  @Test
  void getScoreForUser_existingUser_returnsScore() {
    BountyHunterScore expected =
        new BountyHunterScore(
            "user", 200L, 100L, 100L, BountyHunterLevel.EXPERIENCED, 3, 2, Instant.now());
    when(repository.findScoreByUserId("user")).thenReturn(Optional.of(expected));
    Optional<BountyHunterScore> result = service.getScoreForUser("user");
    assertTrue(result.isPresent());
    assertEquals(expected, result.get());
  }

  @Test
  void getLeaderboard_returnsTopScores() {
    List<BountyHunterScore> expected =
        List.of(
            new BountyHunterScore(
                "user1", 500L, 300L, 200L, BountyHunterLevel.PROFESSIONAL, 10, 8, Instant.now()),
            new BountyHunterScore(
                "user2", 200L, 100L, 100L, BountyHunterLevel.EXPERIENCED, 5, 5, Instant.now()));
    when(repository.findTopScores(10)).thenReturn(expected);
    List<BountyHunterScore> result = service.getLeaderboard(10);
    assertEquals(2, result.size());
  }

  @Test
  void awardIssueCreationPoints_duplicate_returnsExistingScore() {
    String userId = "testuser";
    String issueNumber = "123";
    String label = "bug-impact-medium";
    String validatedBy = "admin";
    long points = 15L;

    when(configService.getPointsForLabel(label)).thenReturn(points);
    when(configService.isAdminUser(validatedBy)).thenReturn(true);
    
    BountyHunterScore existingScore = new BountyHunterScore(userId, 50L, 50L, 0L, BountyHunterLevel.NOVICE, 3, 0, Instant.now());
    when(repository.awardAtomically(eq(userId), eq(issueNumber), eq(BountyHunterEventType.ISSUE_LABEL_APPROVED), any(), any()))
        .thenReturn(existingScore);

    BountyHunterScore result = service.awardIssueCreationPoints(userId, issueNumber, label, validatedBy);

    assertEquals(50L, result.totalPoints()); // Points did not increase
  }

  @Test
  void awardIssueResolutionPoints_duplicate_returnsExistingScore() {
    String userId = "testuser";
    String issueNumber = "127";
    String label = "feature-request";
    long points = 20L;

    when(configService.getPointsForLabel(label)).thenReturn(points);
    
    BountyHunterScore existingScore = new BountyHunterScore(userId, 100L, 0L, 100L, BountyHunterLevel.EXPERIENCED, 0, 2, Instant.now());
    when(repository.awardAtomically(eq(userId), eq(issueNumber), eq(BountyHunterEventType.ISSUE_RESOLVED_BY_PR), any(), any()))
        .thenReturn(existingScore);

    BountyHunterScore result = service.awardIssueResolutionPoints(userId, issueNumber, "45", label);

    assertEquals(100L, result.totalPoints()); // Points did not increase
  }
}
