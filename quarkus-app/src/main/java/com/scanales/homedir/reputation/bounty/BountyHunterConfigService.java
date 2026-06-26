package com.scanales.homedir.reputation.bounty;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration service for Bounty Hunter program.
 * Provides label point mappings and admin user validation.
 */
@ApplicationScoped
public class BountyHunterConfigService {

  // Label weights configuration
  private static final Map<String, Long> LABEL_POINTS =
      Map.ofEntries(
          Map.entry("bug-impact-low", 5L),
          Map.entry("bug-impact-medium", 15L),
          Map.entry("bug-impact-high", 30L),
          Map.entry("feature-request", 20L),
          Map.entry("documentation-improvement", 10L),
          Map.entry("platform-maintenance", 15L),
          Map.entry("enhancement", 12L),
          Map.entry("performance", 18L),
          Map.entry("security", 40L));

  // Administrator accounts authorized to validate issues
  private static final Set<String> ADMIN_USERS = Set.of("admin", "scanales-stack", "os-santiago");

  /**
   * Get point value for a specific label.
   * Returns 0 if label is not eligible for points.
   */
  public long getPointsForLabel(String label) {
    if (label == null || label.isBlank()) {
      return 0L;
    }
    return LABEL_POINTS.getOrDefault(label.trim().toLowerCase(), 0L);
  }

  /**
   * Check if a user is authorized to validate issues and award points.
   */
  public boolean isAdminUser(String userId) {
    if (userId == null || userId.isBlank()) {
      return false;
    }
    return ADMIN_USERS.contains(userId.trim().toLowerCase());
  }

  /**
   * Get all configured label mappings.
   */
  public Map<String, Long> getAllLabelPoints() {
    return Map.copyOf(LABEL_POINTS);
  }

  /**
   * Get all configured admin users.
   */
  public Set<String> getAllAdminUsers() {
    return Set.copyOf(ADMIN_USERS);
  }

  /**
   * Get all eligible labels as IssueImpactLabel objects.
   */
  public List<IssueImpactLabel> getAllEligibleLabels() {
    return LABEL_POINTS.entrySet().stream()
        .map(entry -> new IssueImpactLabel(entry.getKey(), entry.getValue()))
        .sorted(java.util.Comparator.comparingLong(IssueImpactLabel::points).reversed())
        .toList();
  }

  /**
   * Get all bounty hunter levels.
   */
  public List<BountyHunterLevel> getAllLevels() {
    return java.util.Arrays.asList(BountyHunterLevel.values());
  }
}
