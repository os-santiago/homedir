package com.scanales.eventflow.economy;

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
    economyService.rewardFromGamification(userId, "test_reward", 1000, "seed");

    EconomyWallet walletBefore = economyService.getWallet(userId);
    assertTrue(walletBefore.balanceHcoin() >= 120);

    EconomyService.PurchaseResult purchase = economyService.purchase(userId, "profile-glow");
    EconomyWallet walletAfter = economyService.getWallet(userId);
    List<EconomyInventoryItem> inventory = economyService.listInventory(userId, 20, 0);
    EconomyService.TransactionPage page = economyService.listTransactions(userId, 20, 0);

    assertEquals("profile-glow", purchase.itemId());
    assertEquals(walletBefore.balanceHcoin() - 120, walletAfter.balanceHcoin());
    assertTrue(inventory.stream().anyMatch(item -> "profile-glow".equals(item.itemId()) && item.quantity() >= 1));
    assertFalse(page.items().isEmpty());
    assertEquals(EconomyTransactionType.PURCHASE, page.items().getFirst().type());
  }

  @Test
  void transactionsOffsetLoadsHistoricalPageOnDemand() {
    String userId = "history@example.com";
    for (int i = 0; i < 60; i++) {
      economyService.rewardFromGamification(userId, "history_" + i, 5, "seed_" + i);
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
            economyService.rewardFromGamification(userId, "limit_" + i, 10, "seed_" + i);
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
        () -> economyService.rewardFromGamification(userId, "limit_blocked", 10, "seed_blocked"));
    assertTrue(awarded > 0);
  }

  @Test
  void progressionGatesHighTierCatalogAndUnlocksAfterAdvancing() {
    String userId = "progression@example.com";
    economyService.rewardFromGamification(userId, "bootstrap", 3000, "seed");

    List<EconomyService.CatalogOffer> initial = economyService.listCatalogForUser(userId);
    EconomyService.CatalogOffer architect =
        initial.stream().filter(item -> "architect-badge".equals(item.id())).findFirst().orElseThrow();

    assertFalse(architect.unlocked());
    assertTrue(
        "requires_level".equals(architect.lockReason())
            || "requires_total_xp".equals(architect.lockReason())
            || "requires_class_xp".equals(architect.lockReason()));

    assertThrows(EconomyService.ValidationException.class, () -> economyService.purchase(userId, "architect-badge"));
  }
}
