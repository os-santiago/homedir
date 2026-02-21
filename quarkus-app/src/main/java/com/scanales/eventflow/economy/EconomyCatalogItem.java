package com.scanales.eventflow.economy;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EconomyCatalogItem(
    String id,
    String name,
    String description,
    String category,
    @JsonProperty("price_hcoin") int priceHcoin,
    boolean enabled,
    @JsonProperty("max_per_user") int maxPerUser) {
}
