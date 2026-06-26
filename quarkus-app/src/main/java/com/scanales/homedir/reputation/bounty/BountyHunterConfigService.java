package com.scanales.homedir.reputation.bounty;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Configuration service for the Bounty Hunter program.
 * Manages eligible labels, point weights, admin accounts, and level thresholds.
 */
@ApplicationScoped
public class BountyHunterConfigService {

  private static final Map<String, IssueImpactLabel> DEFAULT_LABELS =
      Map.of(
          "bug-impact-low", IssueImpactLabel.bugLow(),
          "bug-impact-medium", IssueImpactLabel.bugMedium(),
          "bug-impact-high", IssueImpactLabel.bugHigh(),
          "feature-request", IssueImpactLabel.featureRequest(),
          "documentation-improvement", IssueImpactLabel.documentationImprovement(),
          "platform-maintenance", IssueImpactLabel.platformMaintenance());

  private static final Set<String> DEFAULT_ADMIN_ACCOUNTS =
      Set.of("os-santiago", "scanales-stack", "admin-github-user");

  public Optional<IssueImpactLabel> findLabelConfig(String labelName) {
    if (labelName == null || labelName.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(DEFAULT_LABELS.get(labelName.trim().toLowerCase()));
  }

  public List<IssueImpactLabel> getAllEligibleLabels() {
    return DEFAULT_LABELS.values().stream().filter(IssueImpactLabel::enabled).toList();
  }

  public boolean isAdminAccount(String githubUsername) {
    if (githubUsername == null || githubUsername.isBlank()) {
      return false;
    }
    return DEFAULT_ADMIN_ACCOUNTS.contains(githubUsername.trim().toLowerCase());
  }

  public Set<String> getAdminAccounts() {
    return Set.copyOf(DEFAULT_ADMIN_ACCOUNTS);
  }

  public long getPointsForLabel(String labelName) {
    return findLabelConfig(labelName).map(IssueImpactLabel::points).orElse(0L);
  }

  public boolean isLabelEligible(String labelName) {
    return findLabelConfig(labelName).map(IssueImpactLabel::enabled).orElse(false);
  }

  public List<BountyHunterLevel> getAllLevels() {
    return List.of(BountyHunterLevel.values());
  }
}
