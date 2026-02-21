package com.scanales.eventflow.economy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EconomyTransaction(
    String id,
    @JsonProperty("user_id") String userId,
    EconomyTransactionType type,
    @JsonProperty("item_id") String itemId,
    String description,
    @JsonProperty("amount_hcoin") long amountHcoin,
    @JsonProperty("balance_after_hcoin") long balanceAfterHcoin,
    @JsonProperty("created_at") Instant createdAt,
    String reference) {
}
