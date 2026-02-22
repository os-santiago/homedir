package com.scanales.eventflow.private_;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AdminMetricsFunnelRowsTest {

  @Test
  void usesCanonicalCountersWhenPresent() {
    Map<String, Long> snap =
        Map.ofEntries(
            Map.entry("funnel:login_success", 11L),
            Map.entry("funnel:auth.login.callback", 99L),
            Map.entry("funnel:community_vote", 7L),
            Map.entry("funnel:community.vote", 77L),
            Map.entry("funnel:community_propose_submit", 5L),
            Map.entry("funnel:community.submission.create", 55L),
            Map.entry("funnel:cfp_submit", 3L),
            Map.entry("funnel:cfp.submission.create", 33L),
            Map.entry("funnel:cfp_approved", 2L),
            Map.entry("funnel:cfp.submission.status.accepted", 22L),
            Map.entry("funnel:board_profile_open", 9L));

    Map<String, Long> byId =
        AdminMetricsResource.buildFunnelRows(snap).stream()
            .collect(Collectors.toMap(AdminMetricsResource.FunnelRow::id, AdminMetricsResource.FunnelRow::count));

    assertEquals(11L, byId.get("login_success"));
    assertEquals(7L, byId.get("community_vote"));
    assertEquals(5L, byId.get("community_propose_submit"));
    assertEquals(3L, byId.get("cfp_submit"));
    assertEquals(2L, byId.get("cfp_approved"));
    assertEquals(9L, byId.get("board_profile_open"));
  }

  @Test
  void fallsBackToLegacyAliasesWhenCanonicalMissing() {
    Map<String, Long> snap =
        Map.of(
            "funnel:auth.login.callback", 8L,
            "funnel:community.vote", 6L,
            "funnel:community.submission.create", 4L,
            "funnel:cfp.submission.create", 3L,
            "funnel:cfp.submission.status.accepted", 1L,
            "funnel:board_profile_open", 2L);

    Map<String, Long> byId = rowsById(snap);

    assertEquals(8L, byId.get("login_success"));
    assertEquals(6L, byId.get("community_vote"));
    assertEquals(4L, byId.get("community_propose_submit"));
    assertEquals(3L, byId.get("cfp_submit"));
    assertEquals(1L, byId.get("cfp_approved"));
    assertEquals(2L, byId.get("board_profile_open"));
  }

  private static Map<String, Long> rowsById(Map<String, Long> snap) {
    return AdminMetricsResource.buildFunnelRows(snap).stream()
        .collect(
            Collectors.toMap(
                AdminMetricsResource.FunnelRow::id,
                AdminMetricsResource.FunnelRow::count,
                (a, b) -> a));
  }
}
