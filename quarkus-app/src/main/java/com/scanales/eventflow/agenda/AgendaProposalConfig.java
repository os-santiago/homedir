package com.scanales.eventflow.agenda;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Persisted config for temporary agenda proposal notices in event detail pages. */
public record AgendaProposalConfig(
    @JsonProperty("proposal_notice_enabled") boolean proposalNoticeEnabled,
    @JsonProperty("updated_at") Instant updatedAt) {

  public static AgendaProposalConfig defaults(boolean proposalNoticeEnabled) {
    return new AgendaProposalConfig(proposalNoticeEnabled, Instant.now());
  }

  public AgendaProposalConfig withProposalNoticeEnabled(boolean enabled) {
    return new AgendaProposalConfig(enabled, Instant.now());
  }
}
