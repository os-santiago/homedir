package com.scanales.homedir.reputation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/** Phase-2 shadow read access for internal/admin diagnostics only. */
@ApplicationScoped
public class ReputationShadowReadService {

  @Inject ReputationFeatureFlags featureFlags;
  @Inject ReputationEngineService engineService;

  public Optional<ReputationEngineService.UserExplainability> user(String userId, Integer limit) {
    if (!isEnabled()) {
      return Optional.empty();
    }
    int safeLimit = limit == null ? 20 : limit;
    return Optional.of(engineService.explainUser(userId, safeLimit));
  }

  public Optional<ReputationEngineService.Diagnostics> diagnostics() {
    if (!isEnabled()) {
      return Optional.empty();
    }
    return Optional.of(engineService.diagnostics(10));
  }

  private boolean isEnabled() {
    ReputationFeatureFlags.Flags flags = featureFlags.snapshot();
    return flags.engineEnabled() && flags.shadowReadEnabled();
  }
}
