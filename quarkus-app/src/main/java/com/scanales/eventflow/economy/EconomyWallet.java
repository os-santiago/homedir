package com.scanales.eventflow.economy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EconomyWallet(
    @JsonProperty("user_id") String userId,
    @JsonProperty("balance_hcoin") long balanceHcoin,
    @JsonProperty("updated_at") Instant updatedAt) {
}
