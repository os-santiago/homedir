package com.scanales.eventflow.challenges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.eventflow.TestDataDir;
import com.scanales.eventflow.economy.EconomyService;
import com.scanales.eventflow.economy.EconomyTransaction;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
class ChallengeServiceTest {

  @Inject ChallengeService challengeService;
  @Inject GamificationService gamificationService;
  @Inject EconomyService economyService;
  @Inject UserProfileService userProfileService;

  @BeforeEach
  void setUp() {
    challengeService.resetForTests();
    economyService.resetForTests();
  }

  @Test
  void communityScoutCompletesAndRewardsOnlyOnce() {
    String userId = "challenge-scout@example.com";
    userProfileService.upsert(userId, "Challenge Scout", userId);

    gamificationService.award(userId, GamificationActivity.COMMUNITY_BOARD_MEMBERS_VIEW, "members");
    gamificationService.award(userId, GamificationActivity.COMMUNITY_VOTE, "pick-1");
    gamificationService.award(userId, GamificationActivity.COMMUNITY_VOTE, "pick-2");
    gamificationService.award(userId, GamificationActivity.COMMUNITY_VOTE, "pick-3");
    gamificationService.award(userId, GamificationActivity.BOARD_PROFILE_OPEN, "member-1");

    ChallengeService.ChallengeProgressCard card =
        challengeService.listProgressForUser(userId).stream()
            .filter(item -> "community-scout".equals(item.id()))
            .findFirst()
            .orElseThrow();

    assertTrue(card.completed());
    assertEquals(5, card.completedSteps());
    assertEquals(5, card.totalSteps());
    assertNotNull(card.completedAt());
    assertNotNull(card.rewardGrantedAt());

    List<EconomyTransaction> transactions = economyService.listTransactions(userId, 100, 0).items();
    long challengeRewards =
        transactions.stream().filter(tx -> "challenge:community-scout".equals(tx.reference())).count();
    assertEquals(1, challengeRewards);
    EconomyTransaction reward =
        transactions.stream()
            .filter(tx -> "challenge:community-scout".equals(tx.reference()))
            .findFirst()
            .orElseThrow();
    assertEquals(30L, reward.amountHcoin());

    gamificationService.award(userId, GamificationActivity.COMMUNITY_VOTE, "pick-4");
    long challengeRewardsAfterRepeat =
        economyService.listTransactions(userId, 100, 0).items().stream()
            .filter(tx -> "challenge:community-scout".equals(tx.reference()))
            .count();
    assertEquals(1, challengeRewardsAfterRepeat);
  }

  @Test
  void openSourceIdentityDerivesExistingProfileLinks() {
    String userId = "identity@example.com";
    userProfileService.upsert(userId, "Identity User", userId);
    userProfileService.linkGithub(
        userId,
        "Identity User",
        userId,
        new UserProfile.GithubAccount("identity-user", "https://github.com/identity-user", null, "gh-1", Instant.now()));
    userProfileService.linkDiscord(
        userId,
        "Identity User",
        userId,
        new UserProfile.DiscordAccount("discord-1", "identity-user", "https://discord.com/users/discord-1", null, Instant.now()));

    ChallengeService.ChallengeProgressCard card =
        challengeService.listProgressForUser(userId).stream()
            .filter(item -> "open-source-identity".equals(item.id()))
            .findFirst()
            .orElseThrow();

    assertTrue(card.completed());
    assertEquals(3, card.completedSteps());
    assertEquals(3, card.totalSteps());
    assertEquals(1, card.activityCounts().getOrDefault("first_login_bonus", 0));
    assertEquals(1, card.activityCounts().getOrDefault("github_linked", 0));
    assertEquals(1, card.activityCounts().getOrDefault("discord_linked", 0));
    assertNotNull(card.rewardGrantedAt());

    List<EconomyTransaction> transactions = economyService.listTransactions(userId, 50, 0).items();
    EconomyTransaction reward =
        transactions.stream()
            .filter(tx -> "challenge:open-source-identity".equals(tx.reference()))
            .findFirst()
            .orElseThrow();
    assertEquals(45L, reward.amountHcoin());
  }
}
