package com.scanales.eventflow.economy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EconomyInventoryItem(
    @JsonProperty("item_id") String itemId,
    String name,
    String category,
    int quantity,
    @JsonProperty("first_acquired_at") Instant firstAcquiredAt,
    @JsonProperty("last_acquired_at") Instant lastAcquiredAt) {
}
