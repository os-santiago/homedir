package com.scanales.homedir.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EconomyServiceTest {

  @Inject EconomyService economyService;

  @BeforeEach
  void setup() {
    economyService.resetForTests();
  }

  @Test
  void purchaseUpdatesWalletInventoryAndTransactions() {
    String userId = "user@example.com";
    economyService.rewardFromGamification(
        userId, "test_reward", 1000, "seed", EconomyService.RewardDeduplication.NONE);

    EconomyWallet walletBefore = economyService.getWallet(userId);
    assertTrue(walletBefore.balanceHcoin() >= 120);

    EconomyService.PurchaseResult purchase = economyService.purchase(userId, "profile-glow");
    EconomyWallet walletAfter = economyService.getWallet(userId);
    List<EconomyInventoryItem> inventory = economyService.listInventory(userId, 20, 0);
    EconomyService.CatalogOffer profileGlow =
        economyService.listCatalogForUser(userId).stream()
            .filter(item -> "profile-glow".equals(item.id()))
            .findFirst()
            .orElseThrow();
    EconomyService.TransactionPage page = economyService.listTransactions(userId, 20, 0);

    assertEquals("profile-glow", purchase.itemId());
    assertEquals(walletBefore.balanceHcoin() - 120, walletAfter.balanceHcoin());
    assertTrue(
        inventory.stream()
            .anyMatch(item -> "profile-glow".equals(item.itemId()) && item.quantity() >= 1));
    assertEquals(1, profileGlow.ownedQuantity());
    assertTrue(profileGlow.remainingStock() >= 0);
    assertFalse(page.items().isEmpty());
    assertEquals(EconomyTransactionType.PURCHASE, page.items().getFirst().type());
  }

  @Test
  void transactionsOffsetLoadsHistoricalPageOnDemand() {
    String userId = "history@example.com";
    for (int i = 0; i < 60; i++) {
      economyService.rewardFromGamification(
          userId, "history_" + i, 5, "seed_" + i, EconomyService.RewardDeduplication.NONE);
    }

    EconomyService.TransactionPage firstPage = economyService.listTransactions(userId, 10, 0);
    EconomyService.TransactionPage deepPage = economyService.listTransactions(userId, 10, 50);

    assertEquals(10, firstPage.items().size());
    assertTrue(firstPage.partial());
    assertEquals(10, deepPage.items().size());
    assertFalse(deepPage.partial());
    assertEquals(60, deepPage.total());
  }

  @Test
  void guardrailBlocksWhenTransactionHistoryLimitIsReached() {
    String userId = "limit@example.com";
    boolean blocked = false;
    int awarded = 0;
    for (int i = 0; i < 400; i++) {
      try {
        EconomyService.RewardResult reward =
            economyService.rewardFromGamification(
                userId, "limit_" + i, 10, "seed_" + i, EconomyService.RewardDeduplication.NONE);
        assertTrue(reward.awarded());
        awarded++;
      } catch (EconomyService.CapacityException expected) {
        blocked = true;
        break;
      }
    }
    assertTrue(blocked, "economy guardrail should block when transaction history reaches limit");
    assertThrows(
        EconomyService.CapacityException.class,
        () ->
            economyService.rewardFromGamification(
                userId, "limit_blocked", 10, "seed_blocked",
                EconomyService.RewardDeduplication.NONE));
    assertTrue(awarded > 0);
  }

  @Test
  void progressionGatesHighTierCatalogAndUnlocksAfterAdvancing() {
    String userId = "progression@example.com";
    economyService.rewardFromGamification(
        userId, "bootstrap", 3000, "seed", EconomyService.RewardDeduplication.NONE);

    List<EconomyService.CatalogOffer> initial = economyService.listCatalogForUser(userId);
    EconomyService.CatalogOffer architect =
        initial.stream()
            .filter(item -> "architect-badge".equals(item.id()))
            .findFirst()
            .orElseThrow();

    assertFalse(architect.unlocked());
    assertTrue(
        "requires_level".equals(architect.lockReason())
            || "requires_total_xp".equals(architect.lockReason())
            || "requires_class_xp".equals(architect.lockReason()));

    assertThrows(
        EconomyService.ValidationException.class,
        () -> economyService.purchase(userId, "architect-badge"));
  }

  @Test
  void perReferenceGamificationRewardIsIdempotent() {
    String userId = "vote.farmer@example.com";
    String reference = "community_vote:content-42";
    EconomyService.RewardDeduplication dedup = EconomyService.RewardDeduplication.PER_REFERENCE;

    EconomyService.RewardResult first =
        economyService.rewardFromGamification(userId, "community_vote", 5, reference, dedup);
    assertTrue(first.awarded());
    long balanceAfterFirst = economyService.getWallet(userId).balanceHcoin();

    for (int replay = 0; replay < 50; replay++) {
      EconomyService.RewardResult repeated =
          economyService.rewardFromGamification(userId, "community_vote", 5, reference, dedup);
      assertFalse(repeated.awarded(), "replaying the same reference must never award again");
    }

    assertEquals(balanceAfterFirst, economyService.getWallet(userId).balanceHcoin());
    long rewardsForReference =
        economyService.listTransactions(userId, 100, 0).items().stream()
            .filter(tx -> tx.type() == EconomyTransactionType.REWARD)
            .filter(tx -> reference.equals(tx.reference()))
            .count();
    assertEquals(1, rewardsForReference, "ledger must hold exactly one reward per reference");
  }

  @Test
  void perReferenceDeduplicationIsScopedPerUserAndPerReference() {
    String userA = "vote.a@example.com";
    String userB = "vote.b@example.com";
    String content42 = "community_vote:content-42";
    String content43 = "community_vote:content-43";
    EconomyService.RewardDeduplication dedup = EconomyService.RewardDeduplication.PER_REFERENCE;

    assertTrue(
        economyService.rewardFromGamification(userA, "community_vote", 5, content42, dedup)
            .awarded());
    assertTrue(
        economyService.rewardFromGamification(userA, "community_vote", 5, content43, dedup)
            .awarded());
    assertTrue(
        economyService.rewardFromGamification(userB, "community_vote", 5, content42, dedup)
            .awarded());
    assertFalse(
        economyService.rewardFromGamification(userB, "community_vote", 5, content42, dedup)
            .awarded());

    long unit = economyService.previewGamificationReward(5);
    assertEquals(2 * unit, economyService.getWallet(userA).balanceHcoin());
    assertEquals(1 * unit, economyService.getWallet(userB).balanceHcoin());
  }

  @Test
  void nonDeduplicatedGamificationRewardStillAwardsEveryTime() {
    String userId = "event.viewer@example.com";
    EconomyService.RewardDeduplication noDedup = EconomyService.RewardDeduplication.NONE;

    // Once-per-day activities legitimately reuse the same reference on a later day, so NONE must
    // keep awarding for a reference that was already used.
    assertTrue(
        economyService.rewardFromGamification(userId, "event_view", 3, "event-1", noDedup)
            .awarded());
    assertTrue(
        economyService.rewardFromGamification(userId, "event_view", 3, "event-1", noDedup)
            .awarded());

    long unit = economyService.previewGamificationReward(3);
    assertEquals(2 * unit, economyService.getWallet(userId).balanceHcoin());
  }

  @Test
  void perReferenceDeduplicationIsSkippedWhenReferenceIsAbsent() {
    String userId = "daily.checkin@example.com";
    EconomyService.RewardDeduplication dedup = EconomyService.RewardDeduplication.PER_REFERENCE;

    assertTrue(
        economyService.rewardFromGamification(userId, "daily_checkin", 10, null, dedup).awarded());
    assertTrue(
        economyService.rewardFromGamification(userId, "daily_checkin", 10, "   ", dedup).awarded());

    long unit = economyService.previewGamificationReward(10);
    assertEquals(2 * unit, economyService.getWallet(userId).balanceHcoin());
    assertTrue(
        economyService.listTransactions(userId, 10, 0).items().stream()
            .noneMatch(tx -> tx.reference() != null),
        "a blank reference must be stored as null rather than as empty text");
  }
}
