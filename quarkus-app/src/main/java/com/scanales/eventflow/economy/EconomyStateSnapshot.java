package com.scanales.eventflow.economy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record EconomyStateSnapshot(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("updated_at") Instant updatedAt,
    Map<String, EconomyWallet> wallets,
    Map<String, List<EconomyInventoryItem>> inventory,
    List<EconomyTransaction> transactions) {

  public static final int SCHEMA_VERSION = 1;

  public EconomyStateSnapshot {
    wallets = wallets == null ? Map.of() : Map.copyOf(wallets);
    inventory = sanitizeInventory(inventory);
    transactions = transactions == null ? List.of() : List.copyOf(transactions);
  }

  public static EconomyStateSnapshot empty() {
    return new EconomyStateSnapshot(SCHEMA_VERSION, Instant.now(), Map.of(), Map.of(), List.of());
  }

  private static Map<String, List<EconomyInventoryItem>> sanitizeInventory(
      Map<String, List<EconomyInventoryItem>> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, List<EconomyInventoryItem>> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, List<EconomyInventoryItem>> entry : raw.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      sanitized.put(
          entry.getKey(),
          entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()));
    }
    return Map.copyOf(sanitized);
  }
}
