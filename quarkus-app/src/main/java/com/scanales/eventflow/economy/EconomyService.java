package com.scanales.eventflow.economy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.eventflow.model.QuestClass;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.PersistenceService;
import com.scanales.eventflow.service.QuestService;
import com.scanales.eventflow.service.SystemErrorService;
import com.scanales.eventflow.service.UserProfileService;
import io.eventflow.notifications.global.GlobalNotification;
import io.eventflow.notifications.global.GlobalNotificationService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EconomyService {
  private static final Logger LOG = Logger.getLogger(EconomyService.class);
  private static final int MAX_PAGE_LIMIT = 100;
  private static final List<EconomyCatalogItem> DEFAULT_CATALOG =
      List.of(
          new EconomyCatalogItem(
              "profile-glow",
              "Profile Glow",
              "Unlocks a featured glow style for your public profile.",
              "profile",
              120,
              true,
              1),
          new EconomyCatalogItem(
              "community-spotlight",
              "Community Spotlight",
              "Highlight one approved contribution in Community Picks.",
              "community",
              240,
              true,
              4),
          new EconomyCatalogItem(
              "event-fast-pass",
              "Event Fast Pass",
              "Priority badge for event and CFP participation views.",
              "events",
              160,
              true,
              2),
          new EconomyCatalogItem(
              "architect-badge",
              "Architect Badge",
              "Exclusive badge for advanced contributors in Homedir.",
              "reputation",
              300,
              true,
              1));
  private static final CatalogPolicy FALLBACK_POLICY =
      new CatalogPolicy("COMMON", 1, 0, null, 0, 50, 1, 15, 1, 3);
  private static final Map<String, CatalogPolicy> CATALOG_POLICIES =
      Map.of(
          "profile-glow",
          new CatalogPolicy("COMMON", 1, 0, null, 0, 30, 1, 10, 1, 3),
          "event-fast-pass",
          new CatalogPolicy("RARE", 3, 120, QuestClass.WARRIOR, 30, 25, 1, 8, 1, 4),
          "community-spotlight",
          new CatalogPolicy("EPIC", 5, 260, QuestClass.MAGE, 80, 20, 1, 6, 1, 5),
          "architect-badge",
          new CatalogPolicy("LEGENDARY", 8, 500, QuestClass.ENGINEER, 160, 15, 1, 5, 1, 6));

  @Inject PersistenceService persistenceService;
  @Inject SystemErrorService systemErrorService;
  @Inject ObjectMapper objectMapper;
  @Inject UserProfileService userProfileService;
  @Inject QuestService questService;
  @Inject Instance<GlobalNotificationService> globalNotificationService;

  @ConfigProperty(name = "economy.transactions.persisted-max", defaultValue = "20000")
  int transactionsPersistedMax;

  @ConfigProperty(name = "economy.transactions.cache-max", defaultValue = "500")
  int transactionsCacheMax;

  @ConfigProperty(name = "economy.storage.max-bytes", defaultValue = "5242880")
  long storageMaxBytes;

  @ConfigProperty(name = "economy.memory.max-users", defaultValue = "50000")
  int memoryMaxUsers;

  @ConfigProperty(name = "economy.memory.max-inventory-entries", defaultValue = "100000")
  int memoryMaxInventoryEntries;

  @ConfigProperty(name = "economy.inventory.user-max-items", defaultValue = "200")
  int userMaxInventoryItems;

  @ConfigProperty(name = "economy.guard.strict", defaultValue = "true")
  boolean guardStrict;

  @ConfigProperty(name = "economy.guard.alert-cooldown", defaultValue = "PT15M")
  Duration guardAlertCooldown;

  @ConfigProperty(name = "economy.rewards.xp-to-hcoin-ratio", defaultValue = "0.2")
  double xpToHcoinRatio;

  @ConfigProperty(name = "economy.rewards.min-hcoin", defaultValue = "1")
  int minRewardHcoin;

  @ConfigProperty(name = "economy.purchase.max-per-minute", defaultValue = "12")
  int purchaseMaxPerMinute;

  private final Object stateLock = new Object();
  private final Map<String, EconomyWallet> wallets = new LinkedHashMap<>();
  private final Map<String, Map<String, EconomyInventoryItem>> inventoryByUser = new LinkedHashMap<>();
  private volatile List<EconomyTransaction> recentTransactionCache = List.of();
  private volatile long totalTransactions;
  private volatile long lastKnownStateMtime = Long.MIN_VALUE;
  private volatile Instant lastLoadTime;
  private volatile long lastLoadDurationMs;
  private volatile String lastGuardrailCode;
  private volatile Instant lastGuardrailAt;
  private final Map<String, Instant> alertHistory = new ConcurrentHashMap<>();

  @PostConstruct
  void init() {
    synchronized (stateLock) {
      refreshFromDisk(true);
    }
  }

  public List<EconomyCatalogItem> listCatalog() {
    return DEFAULT_CATALOG.stream().filter(EconomyCatalogItem::enabled).toList();
  }

  public List<CatalogOffer> listCatalogForUser(String userId) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null) {
      throw new ValidationException("invalid_user_id");
    }
    synchronized (stateLock) {
      refreshFromDisk(false);
      UserProgress progress = loadUserProgress(normalizedUserId);
      Map<String, EconomyInventoryItem> userInventory =
          inventoryByUser.getOrDefault(normalizedUserId, Map.of());
      List<CatalogOffer> offers = new ArrayList<>();
      for (EconomyCatalogItem item : listCatalog()) {
        CatalogPolicy policy = policyFor(item.id());
        int quantityOwned = quantityOwned(userInventory, item.id());
        int currentClassXp = classXpFor(progress, policy.requiredClass());
        int effectiveMax = effectiveMaxPerUser(item, policy, progress);
        AccessDecision access =
            evaluateProgressAccess(
                policy, progress.level(), progress.totalXp(), currentClassXp, quantityOwned, effectiveMax);
        offers.add(
            new CatalogOffer(
                item.id(),
                item.name(),
                item.description(),
                item.category(),
                item.priceHcoin(),
                item.enabled(),
                item.maxPerUser(),
                policy.tier(),
                policy.minLevel(),
                policy.minTotalXp(),
                policy.requiredClass() != null ? policy.requiredClass().name() : null,
                policy.minClassXp(),
                progress.level(),
                progress.totalXp(),
                currentClassXp,
                effectiveMax,
                quantityOwned,
                Math.max(0, effectiveMax - quantityOwned),
                access.unlocked(),
                access.reasonCode()));
      }
      return List.copyOf(offers);
    }
  }

  public Optional<EconomyCatalogItem> findCatalogItem(String itemId) {
    if (itemId == null || itemId.isBlank()) {
      return Optional.empty();
    }
    String normalized = itemId.trim().toLowerCase(Locale.ROOT);
    return listCatalog().stream().filter(item -> normalized.equals(item.id())).findFirst();
  }

  public EconomyWallet getWallet(String userId) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null) {
      throw new ValidationException("invalid_user_id");
    }
    synchronized (stateLock) {
      refreshFromDisk(false);
      return wallets.getOrDefault(normalizedUserId, zeroWallet(normalizedUserId));
    }
  }

  public List<EconomyInventoryItem> listInventory(String userId, int requestedLimit, int requestedOffset) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null) {
      throw new ValidationException("invalid_user_id");
    }
    int limit = normalizeLimit(requestedLimit);
    int offset = Math.max(0, requestedOffset);
    synchronized (stateLock) {
      refreshFromDisk(false);
      Map<String, EconomyInventoryItem> items = inventoryByUser.getOrDefault(normalizedUserId, Map.of());
      List<EconomyInventoryItem> sorted =
          items.values().stream().sorted((a, b) -> safeLower(a.name()).compareTo(safeLower(b.name()))).toList();
      return paginate(sorted, limit, offset);
    }
  }

  public TransactionPage listTransactions(String userId, int requestedLimit, int requestedOffset) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null) {
      throw new ValidationException("invalid_user_id");
    }
    int limit = normalizeLimit(requestedLimit);
    int offset = Math.max(0, requestedOffset);
    synchronized (stateLock) {
      refreshFromDisk(false);
      List<EconomyTransaction> cached = filterTransactionsByUser(recentTransactionCache, normalizedUserId);
      if (offset == 0 && (!cached.isEmpty() || totalTransactions <= recentTransactionCache.size())) {
        return new TransactionPage(
            paginate(cached, limit, offset),
            limit,
            offset,
            cached.size(),
            totalTransactions > recentTransactionCache.size());
      }
      List<EconomyTransaction> full = filterTransactionsByUser(loadFullTransactions(), normalizedUserId);
      return new TransactionPage(
          paginate(full, limit, offset),
          limit,
          offset,
          full.size(),
          false);
    }
  }

  public PurchaseResult purchase(String userId, String itemId) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null) {
      throw new ValidationException("invalid_user_id");
    }
    EconomyCatalogItem catalogItem =
        findCatalogItem(itemId).orElseThrow(() -> new ValidationException("item_not_found"));
    if (!catalogItem.enabled()) {
      throw new ValidationException("item_unavailable");
    }

    synchronized (stateLock) {
      refreshFromDisk(false);
      enforcePurchaseBurstGuard(normalizedUserId);
      List<EconomyTransaction> history = loadFullTransactions();
      Map<String, EconomyWallet> walletCopy = new LinkedHashMap<>(wallets);
      Map<String, Map<String, EconomyInventoryItem>> inventoryCopy = deepCopyInventory(inventoryByUser);

      EconomyWallet currentWallet = walletCopy.getOrDefault(normalizedUserId, zeroWallet(normalizedUserId));
      long updatedBalance = currentWallet.balanceHcoin() - Math.max(0, catalogItem.priceHcoin());
      if (updatedBalance < 0) {
        throw new ValidationException("insufficient_balance");
      }

      Map<String, EconomyInventoryItem> userInventory =
          inventoryCopy.computeIfAbsent(normalizedUserId, ignored -> new LinkedHashMap<>());
      EconomyInventoryItem existingItem = userInventory.get(catalogItem.id());
      int currentQty = existingItem == null ? 0 : Math.max(0, existingItem.quantity());
      UserProgress progress = loadUserProgress(normalizedUserId);
      CatalogPolicy policy = policyFor(catalogItem.id());
      int currentClassXp = classXpFor(progress, policy.requiredClass());
      int effectiveMax = effectiveMaxPerUser(catalogItem, policy, progress);
      AccessDecision access =
          evaluateProgressAccess(
              policy, progress.level(), progress.totalXp(), currentClassXp, currentQty, effectiveMax);
      if (!access.unlocked()) {
        throw new ValidationException(access.reasonCode());
      }
      if (currentQty >= effectiveMax) {
        throw new ValidationException("item_limit_reached");
      }
      if (existingItem == null && userInventory.size() >= Math.max(1, userMaxInventoryItems)) {
        guardrail("user_inventory_limit_reached", normalizedUserId, "max distinct items per user reached");
      }

      boolean newUser = !walletCopy.containsKey(normalizedUserId);
      if (newUser && walletCopy.size() >= Math.max(1, memoryMaxUsers)) {
        guardrail("memory_user_limit_reached", normalizedUserId, "max users in memory reached");
      }
      int inventoryEntries = countInventoryEntries(inventoryCopy);
      if (existingItem == null && inventoryEntries >= Math.max(1, memoryMaxInventoryEntries)) {
        guardrail("memory_inventory_limit_reached", normalizedUserId, "max inventory entries reached");
      }
      if (history.size() >= Math.max(1, transactionsPersistedMax)) {
        guardrail("transaction_history_limit_reached", normalizedUserId, "max persisted transactions reached");
      }
      if (persistenceService.isLowDiskSpace()) {
        guardrail("low_disk_space", normalizedUserId, "persistent storage low disk space");
      }

      Instant now = Instant.now();
      walletCopy.put(normalizedUserId, new EconomyWallet(normalizedUserId, updatedBalance, now));
      userInventory.put(
          catalogItem.id(),
          new EconomyInventoryItem(
              catalogItem.id(),
              catalogItem.name(),
              catalogItem.category(),
              currentQty + 1,
              existingItem == null ? now : existingItem.firstAcquiredAt(),
              now));
      history.add(
          0,
          new EconomyTransaction(
              UUID.randomUUID().toString(),
              normalizedUserId,
              EconomyTransactionType.PURCHASE,
              catalogItem.id(),
              "Purchased " + catalogItem.name(),
              -Math.max(0, catalogItem.priceHcoin()),
              updatedBalance,
              now,
              "shop:" + catalogItem.id()));

      EconomyStateSnapshot candidate = toSnapshot(walletCopy, inventoryCopy, history, now);
      enforceStorageBudget(candidate, normalizedUserId);
      persistSync(candidate);
      applySnapshot(candidate);
      return new PurchaseResult(
          catalogItem.id(),
          catalogItem.name(),
          catalogItem.priceHcoin(),
          updatedBalance,
          userInventory.get(catalogItem.id()).quantity(),
          now);
    }
  }

  public RewardResult rewardFromGamification(
      String userId, String activityKey, int xp, String reference) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null || xp <= 0) {
      return RewardResult.notAwarded();
    }
    int rewardAmount = Math.max(Math.max(1, minRewardHcoin), (int) Math.round(xp * xpToHcoinRatio));
    synchronized (stateLock) {
      refreshFromDisk(false);
      if (persistenceService.isLowDiskSpace()) {
        guardrail("low_disk_space", normalizedUserId, "persistent storage low disk space");
      }
      List<EconomyTransaction> history = loadFullTransactions();
      if (history.size() >= Math.max(1, transactionsPersistedMax)) {
        guardrail("transaction_history_limit_reached", normalizedUserId, "max persisted transactions reached");
      }
      Map<String, EconomyWallet> walletCopy = new LinkedHashMap<>(wallets);
      if (!walletCopy.containsKey(normalizedUserId) && walletCopy.size() >= Math.max(1, memoryMaxUsers)) {
        guardrail("memory_user_limit_reached", normalizedUserId, "max users in memory reached");
      }

      Instant now = Instant.now();
      EconomyWallet currentWallet = walletCopy.getOrDefault(normalizedUserId, zeroWallet(normalizedUserId));
      long updatedBalance = currentWallet.balanceHcoin() + rewardAmount;
      walletCopy.put(normalizedUserId, new EconomyWallet(normalizedUserId, updatedBalance, now));

      history.add(
          0,
          new EconomyTransaction(
              UUID.randomUUID().toString(),
              normalizedUserId,
              EconomyTransactionType.REWARD,
              null,
              "Gamification reward: " + safeText(activityKey, "activity"),
              rewardAmount,
              updatedBalance,
              now,
              reference));
      EconomyStateSnapshot candidate = toSnapshot(walletCopy, inventoryByUser, history, now);
      enforceStorageBudget(candidate, normalizedUserId);
      persistSync(candidate);
      applySnapshot(candidate);
      return new RewardResult(true, rewardAmount, updatedBalance, now);
    }
  }

  public EconomyMetrics metrics() {
    synchronized (stateLock) {
      refreshFromDisk(false);
      return new EconomyMetrics(
          wallets.size(),
          inventoryByUser.size(),
          countInventoryEntries(inventoryByUser),
          totalTransactions,
          recentTransactionCache.size(),
          lastLoadTime,
          lastLoadDurationMs,
          lastGuardrailCode,
          lastGuardrailAt,
          persistenceService.economyStatePath(),
          persistenceService.economyStateSizeBytes(),
          persistenceService.economyStateLastModifiedMillis());
    }
  }

  public void resetForTests() {
    synchronized (stateLock) {
      EconomyStateSnapshot empty = EconomyStateSnapshot.empty();
      persistSync(empty);
      applySnapshot(empty);
    }
  }

  private void persistSync(EconomyStateSnapshot snapshot) {
    try {
      persistenceService.saveEconomyStateSync(snapshot);
      lastKnownStateMtime = persistenceService.economyStateLastModifiedMillis();
    } catch (IllegalStateException e) {
      guardrail("persistence_error", null, safeText(e.getMessage(), "failed_to_persist_economy_state"));
      throw e;
    }
  }

  private void enforceStorageBudget(EconomyStateSnapshot snapshot, String userId) {
    long estimatedBytes;
    try {
      estimatedBytes = objectMapper.writeValueAsBytes(snapshot).length;
    } catch (Exception e) {
      throw new CapacityException("state_serialization_failed");
    }
    if (estimatedBytes > Math.max(1024L, storageMaxBytes)) {
      guardrail(
          "storage_budget_exceeded",
          userId,
          "economy state exceeded max bytes: " + estimatedBytes + "/" + storageMaxBytes);
    }
  }

  private void refreshFromDisk(boolean force) {
    long diskMtime = persistenceService.economyStateLastModifiedMillis();
    if (!force && diskMtime == lastKnownStateMtime) {
      return;
    }
    Instant start = Instant.now();
    EconomyStateSnapshot snapshot = persistenceService.loadEconomyState().orElse(EconomyStateSnapshot.empty());
    applySnapshot(snapshot);
    lastKnownStateMtime = diskMtime;
    lastLoadTime = Instant.now();
    lastLoadDurationMs = Duration.between(start, lastLoadTime).toMillis();
  }

  private void applySnapshot(EconomyStateSnapshot snapshot) {
    wallets.clear();
    wallets.putAll(snapshot.wallets());
    inventoryByUser.clear();
    for (Map.Entry<String, List<EconomyInventoryItem>> entry : snapshot.inventory().entrySet()) {
      Map<String, EconomyInventoryItem> byItemId = new LinkedHashMap<>();
      for (EconomyInventoryItem item : entry.getValue()) {
        if (item == null || item.itemId() == null || item.itemId().isBlank()) {
          continue;
        }
        byItemId.put(item.itemId(), item);
      }
      inventoryByUser.put(entry.getKey(), byItemId);
    }
    totalTransactions = snapshot.transactions().size();
    int cacheLimit = Math.max(0, transactionsCacheMax);
    if (cacheLimit == 0 || snapshot.transactions().isEmpty()) {
      recentTransactionCache = List.of();
    } else {
      int end = Math.min(snapshot.transactions().size(), cacheLimit);
      recentTransactionCache = List.copyOf(snapshot.transactions().subList(0, end));
    }
  }

  private List<EconomyTransaction> loadFullTransactions() {
    if (totalTransactions <= recentTransactionCache.size()) {
      return new ArrayList<>(recentTransactionCache);
    }
    return new ArrayList<>(
        persistenceService
            .loadEconomyState()
            .map(EconomyStateSnapshot::transactions)
            .orElse(List.of()));
  }

  private void enforcePurchaseBurstGuard(String userId) {
    int maxBurst = Math.max(1, purchaseMaxPerMinute);
    Instant since = Instant.now().minus(Duration.ofMinutes(1));
    int purchasesInWindow = 0;
    for (EconomyTransaction tx : recentTransactionCache) {
      if (tx == null || tx.type() != EconomyTransactionType.PURCHASE) {
        continue;
      }
      if (!userId.equals(tx.userId())) {
        continue;
      }
      if (tx.createdAt() != null && tx.createdAt().isBefore(since)) {
        continue;
      }
      purchasesInWindow++;
      if (purchasesInWindow >= maxBurst) {
        guardrail("purchase_rate_limit_reached", userId, "max purchases per minute reached");
      }
    }
  }

  private UserProgress loadUserProgress(String userId) {
    UserProfile profile = userProfileService.find(userId).orElse(null);
    int totalXp = profile != null ? Math.max(0, profile.getCurrentXp()) : 0;
    int level = questService.calculateLevel(totalXp);
    int achievements = countAchievements(profile);
    Map<QuestClass, Integer> classXp = new EnumMap<>(QuestClass.class);
    if (profile != null && profile.getClassXp() != null) {
      for (Map.Entry<QuestClass, Integer> entry : profile.getClassXp().entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) {
          continue;
        }
        classXp.put(entry.getKey(), Math.max(0, entry.getValue()));
      }
    }
    return new UserProgress(totalXp, level, achievements, classXp);
  }

  private static int countAchievements(UserProfile profile) {
    if (profile == null || profile.getHistory() == null || profile.getHistory().isEmpty()) {
      return 0;
    }
    Set<String> unique = new HashSet<>();
    for (UserProfile.QuestHistoryItem item : profile.getHistory()) {
      if (item == null || item.xp() <= 0 || item.title() == null) {
        continue;
      }
      String title = item.title().trim().toLowerCase(Locale.ROOT);
      if (!title.isBlank()) {
        unique.add(title);
      }
    }
    return unique.size();
  }

  private CatalogPolicy policyFor(String itemId) {
    if (itemId == null) {
      return FALLBACK_POLICY;
    }
    return CATALOG_POLICIES.getOrDefault(itemId, FALLBACK_POLICY);
  }

  private static int quantityOwned(Map<String, EconomyInventoryItem> userInventory, String itemId) {
    EconomyInventoryItem entry = userInventory.get(itemId);
    return entry == null ? 0 : Math.max(0, entry.quantity());
  }

  private static int classXpFor(UserProgress progress, QuestClass requiredClass) {
    if (requiredClass == null || progress == null || progress.classXp() == null) {
      return 0;
    }
    return Math.max(0, progress.classXp().getOrDefault(requiredClass, 0));
  }

  private static int effectiveMaxPerUser(
      EconomyCatalogItem item, CatalogPolicy policy, UserProgress progress) {
    int base = Math.max(1, item.maxPerUser());
    int levelBonus = progressionBonus(progress.level() - policy.minLevel(), policy.levelStep(), policy.stockPerLevelStep());
    int achievementBonus =
        progressionBonus(progress.achievements(), policy.achievementStep(), policy.stockPerAchievementStep());
    int bonus = Math.min(Math.max(0, policy.bonusCap()), Math.max(0, levelBonus + achievementBonus));
    return base + bonus;
  }

  private static int progressionBonus(int value, int step, int incrementPerStep) {
    if (value <= 0 || step <= 0 || incrementPerStep <= 0) {
      return 0;
    }
    return (value / step) * incrementPerStep;
  }

  private static AccessDecision evaluateProgressAccess(
      CatalogPolicy policy,
      int currentLevel,
      int currentTotalXp,
      int currentClassXp,
      int quantityOwned,
      int effectiveMaxPerUser) {
    if (currentLevel < policy.minLevel()) {
      return new AccessDecision(false, "requires_level");
    }
    if (currentTotalXp < policy.minTotalXp()) {
      return new AccessDecision(false, "requires_total_xp");
    }
    if (policy.requiredClass() != null && currentClassXp < policy.minClassXp()) {
      return new AccessDecision(false, "requires_class_xp");
    }
    if (quantityOwned >= effectiveMaxPerUser) {
      return new AccessDecision(false, "item_limit_reached");
    }
    return new AccessDecision(true, null);
  }

  private EconomyStateSnapshot toSnapshot(
      Map<String, EconomyWallet> walletSnapshot,
      Map<String, Map<String, EconomyInventoryItem>> inventorySnapshot,
      List<EconomyTransaction> transactionsSnapshot,
      Instant updatedAt) {
    Map<String, List<EconomyInventoryItem>> inventory = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, EconomyInventoryItem>> entry : inventorySnapshot.entrySet()) {
      inventory.put(entry.getKey(), List.copyOf(entry.getValue().values()));
    }
    return new EconomyStateSnapshot(
        EconomyStateSnapshot.SCHEMA_VERSION,
        updatedAt,
        walletSnapshot,
        inventory,
        transactionsSnapshot);
  }

  private void guardrail(String code, String userId, String detail) {
    lastGuardrailCode = code;
    lastGuardrailAt = Instant.now();
    emitGuardrailAlert(code, userId, detail);
    if (guardStrict) {
      throw new CapacityException(code);
    }
    LOG.warnf("economy_guardrail_non_strict code=%s detail=%s", code, detail);
  }

  private void emitGuardrailAlert(String code, String userId, String detail) {
    Instant now = Instant.now();
    Instant previous = alertHistory.get(code);
    if (previous != null && guardAlertCooldown != null && now.isBefore(previous.plus(guardAlertCooldown))) {
      return;
    }
    alertHistory.put(code, now);
    String message = "Economy guardrail triggered: " + safeText(code, "unknown");
    systemErrorService.logError("WARN", "EconomyService", message + " (" + safeText(detail, "-") + ")", null, userId);
    LOG.warnf("economy_guardrail_triggered code=%s user=%s detail=%s", code, userId, detail);
    if (!globalNotificationService.isResolvable()) {
      return;
    }
    try {
      GlobalNotification alert = new GlobalNotification();
      alert.id = UUID.randomUUID().toString();
      alert.type = "ECONOMY_GUARDRAIL";
      alert.category = "announcement";
      alert.title = "Economy guardrail";
      alert.message = safeText(code, "unknown");
      alert.targetUrl = "/private/admin/metrics";
      alert.createdAt = now.toEpochMilli();
      alert.dedupeKey = "economy:" + code;
      globalNotificationService.get().enqueue(alert);
    } catch (Exception e) {
      LOG.debug("Unable to emit global guardrail notification", e);
    }
  }

  private static String normalizeUserId(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
  }

  private static EconomyWallet zeroWallet(String userId) {
    return new EconomyWallet(userId, 0L, null);
  }

  private static int normalizeLimit(int requestedLimit) {
    if (requestedLimit <= 0) {
      return 20;
    }
    return Math.min(requestedLimit, MAX_PAGE_LIMIT);
  }

  private static <T> List<T> paginate(List<T> source, int limit, int offset) {
    if (source.isEmpty() || offset >= source.size()) {
      return List.of();
    }
    int end = Math.min(source.size(), offset + limit);
    return source.subList(offset, end);
  }

  private static List<EconomyTransaction> filterTransactionsByUser(
      List<EconomyTransaction> source, String userId) {
    if (source.isEmpty()) {
      return List.of();
    }
    return source.stream().filter(tx -> userId.equals(tx.userId())).toList();
  }

  private static Map<String, Map<String, EconomyInventoryItem>> deepCopyInventory(
      Map<String, Map<String, EconomyInventoryItem>> source) {
    Map<String, Map<String, EconomyInventoryItem>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, EconomyInventoryItem>> entry : source.entrySet()) {
      copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    return copy;
  }

  private static int countInventoryEntries(Map<String, Map<String, EconomyInventoryItem>> source) {
    int total = 0;
    for (Map<String, EconomyInventoryItem> entries : source.values()) {
      total += entries.size();
    }
    return total;
  }

  private static String safeLower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private static String safeText(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }

  public record CatalogOffer(
      String id,
      String name,
      String description,
      String category,
      @com.fasterxml.jackson.annotation.JsonProperty("price_hcoin") int priceHcoin,
      boolean enabled,
      @com.fasterxml.jackson.annotation.JsonProperty("max_per_user") int maxPerUser,
      String tier,
      @com.fasterxml.jackson.annotation.JsonProperty("required_level") int requiredLevel,
      @com.fasterxml.jackson.annotation.JsonProperty("required_total_xp") int requiredTotalXp,
      @com.fasterxml.jackson.annotation.JsonProperty("required_class") String requiredClass,
      @com.fasterxml.jackson.annotation.JsonProperty("required_class_xp") int requiredClassXp,
      @com.fasterxml.jackson.annotation.JsonProperty("current_level") int currentLevel,
      @com.fasterxml.jackson.annotation.JsonProperty("current_total_xp") int currentTotalXp,
      @com.fasterxml.jackson.annotation.JsonProperty("current_class_xp") int currentClassXp,
      @com.fasterxml.jackson.annotation.JsonProperty("effective_max_per_user") int effectiveMaxPerUser,
      @com.fasterxml.jackson.annotation.JsonProperty("owned_quantity") int ownedQuantity,
      @com.fasterxml.jackson.annotation.JsonProperty("remaining_stock") int remainingStock,
      boolean unlocked,
      @com.fasterxml.jackson.annotation.JsonProperty("lock_reason") String lockReason) {
  }

  public record PurchaseResult(
      String itemId,
      String itemName,
      int priceHcoin,
      long balanceAfterHcoin,
      int quantityOwned,
      Instant createdAt) {
  }

  public record RewardResult(
      boolean awarded,
      long amountHcoin,
      long balanceAfterHcoin,
      Instant createdAt) {
    public static RewardResult notAwarded() {
      return new RewardResult(false, 0L, 0L, Instant.now());
    }
  }

  public record TransactionPage(
      List<EconomyTransaction> items,
      int limit,
      int offset,
      long total,
      boolean partial) {
  }

  public record EconomyMetrics(
      int walletUsers,
      int inventoryUsers,
      int inventoryEntries,
      long totalTransactions,
      int cachedTransactions,
      Instant lastLoadTime,
      long lastLoadDurationMs,
      String lastGuardrailCode,
      Instant lastGuardrailAt,
      String statePath,
      long stateSizeBytes,
      long stateLastModifiedMillis) {
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class CapacityException extends RuntimeException {
    public CapacityException(String message) {
      super(message);
    }
  }

  private record CatalogPolicy(
      String tier,
      int minLevel,
      int minTotalXp,
      QuestClass requiredClass,
      int minClassXp,
      int levelStep,
      int stockPerLevelStep,
      int achievementStep,
      int stockPerAchievementStep,
      int bonusCap) {
  }

  private record UserProgress(int totalXp, int level, int achievements, Map<QuestClass, Integer> classXp) {
  }

  private record AccessDecision(boolean unlocked, String reasonCode) {
  }
}
