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
            Map.entry("funnel:community_lightning_post", 4L),
            Map.entry("funnel:community.lightning.thread.create", 44L),
            Map.entry("funnel:community_lightning_comment", 6L),
            Map.entry("funnel:community.lightning.comment.create", 66L),
            Map.entry("funnel:cfp_submit", 3L),
            Map.entry("funnel:cfp.submission.create", 33L),
            Map.entry("funnel:cfp_approved", 2L),
            Map.entry("funnel:cfp.submission.status.accepted", 22L),
            Map.entry("funnel:volunteer_submit", 4L),
            Map.entry("funnel:volunteer.submission.create", 44L),
            Map.entry("funnel:volunteer_selected", 1L),
            Map.entry("funnel:volunteer.submission.status.selected", 11L),
            Map.entry("funnel:volunteer_lounge_post", 2L),
            Map.entry("funnel:volunteer.lounge.post", 22L),
            Map.entry("funnel:challenge.started", 6L),
            Map.entry("funnel:challenge.completed", 3L),
            Map.entry("funnel:board_profile_open", 9L));

    Map<String, Long> byId =
        AdminMetricsResource.buildFunnelRows(snap).stream()
            .collect(Collectors.toMap(AdminMetricsResource.FunnelRow::id, AdminMetricsResource.FunnelRow::count));

    assertEquals(11L, byId.get("login_success"));
    assertEquals(7L, byId.get("community_vote"));
    assertEquals(5L, byId.get("community_propose_submit"));
    assertEquals(4L, byId.get("community_lightning_post"));
    assertEquals(6L, byId.get("community_lightning_comment"));
    assertEquals(3L, byId.get("cfp_submit"));
    assertEquals(2L, byId.get("cfp_approved"));
    assertEquals(4L, byId.get("volunteer_submit"));
    assertEquals(1L, byId.get("volunteer_selected"));
    assertEquals(2L, byId.get("volunteer_lounge_post"));
    assertEquals(6L, byId.get("challenge_started"));
    assertEquals(3L, byId.get("challenge_completed"));
    assertEquals(9L, byId.get("board_profile_open"));

    Map<String, String> conversions = conversionById(snap);
    assertEquals("100%", conversions.get("login_success"));
    assertEquals("63.6%", conversions.get("community_vote"));
    assertEquals("45.5%", conversions.get("community_propose_submit"));
    assertEquals("36.4%", conversions.get("community_lightning_post"));
    assertEquals("54.5%", conversions.get("community_lightning_comment"));
    assertEquals("27.3%", conversions.get("cfp_submit"));
    assertEquals("18.2%", conversions.get("cfp_approved"));
    assertEquals("36.4%", conversions.get("volunteer_submit"));
    assertEquals("9.1%", conversions.get("volunteer_selected"));
    assertEquals("18.2%", conversions.get("volunteer_lounge_post"));
    assertEquals("54.5%", conversions.get("challenge_started"));
    assertEquals("27.3%", conversions.get("challenge_completed"));
    assertEquals("81.8%", conversions.get("board_profile_open"));
  }

  @Test
  void fallsBackToLegacyAliasesWhenCanonicalMissing() {
    Map<String, Long> snap =
        Map.ofEntries(
            Map.entry("funnel:auth.login.callback", 8L),
            Map.entry("funnel:community.vote", 6L),
            Map.entry("funnel:community.submission.create", 4L),
            Map.entry("funnel:community.lightning.thread.create", 5L),
            Map.entry("funnel:community.lightning.comment.create", 2L),
            Map.entry("funnel:cfp.submission.create", 3L),
            Map.entry("funnel:cfp.submission.status.accepted", 1L),
            Map.entry("funnel:volunteer.submission.create", 2L),
            Map.entry("funnel:volunteer.submission.status.selected", 1L),
            Map.entry("funnel:volunteer.lounge.post", 4L),
            Map.entry("funnel:challenge.started", 5L),
            Map.entry("funnel:challenge.completed", 2L),
            Map.entry("funnel:board_profile_open", 2L));

    Map<String, Long> byId = rowsById(snap);

    assertEquals(8L, byId.get("login_success"));
    assertEquals(6L, byId.get("community_vote"));
    assertEquals(4L, byId.get("community_propose_submit"));
    assertEquals(5L, byId.get("community_lightning_post"));
    assertEquals(2L, byId.get("community_lightning_comment"));
    assertEquals(3L, byId.get("cfp_submit"));
    assertEquals(1L, byId.get("cfp_approved"));
    assertEquals(2L, byId.get("volunteer_submit"));
    assertEquals(1L, byId.get("volunteer_selected"));
    assertEquals(4L, byId.get("volunteer_lounge_post"));
    assertEquals(5L, byId.get("challenge_started"));
    assertEquals(2L, byId.get("challenge_completed"));
    assertEquals(2L, byId.get("board_profile_open"));

    Map<String, String> conversions = conversionById(snap);
    assertEquals("100%", conversions.get("login_success"));
    assertEquals("75.0%", conversions.get("community_vote"));
    assertEquals("50.0%", conversions.get("community_propose_submit"));
    assertEquals("62.5%", conversions.get("community_lightning_post"));
    assertEquals("25.0%", conversions.get("community_lightning_comment"));
    assertEquals("37.5%", conversions.get("cfp_submit"));
    assertEquals("12.5%", conversions.get("cfp_approved"));
    assertEquals("25.0%", conversions.get("volunteer_submit"));
    assertEquals("12.5%", conversions.get("volunteer_selected"));
    assertEquals("50.0%", conversions.get("volunteer_lounge_post"));
    assertEquals("62.5%", conversions.get("challenge_started"));
    assertEquals("25.0%", conversions.get("challenge_completed"));
    assertEquals("25.0%", conversions.get("board_profile_open"));
  }

  @Test
  void usesDashWhenNoLoginBaselineExists() {
    Map<String, Long> snap = Map.of("funnel:community_vote", 3L);
    Map<String, String> conversions = conversionById(snap);
    assertEquals("—", conversions.get("community_vote"));
    assertEquals("—", conversions.get("community_lightning_post"));
    assertEquals("—", conversions.get("community_lightning_comment"));
    assertEquals("—", conversions.get("cfp_submit"));
    assertEquals("—", conversions.get("volunteer_submit"));
    assertEquals("—", conversions.get("volunteer_selected"));
    assertEquals("—", conversions.get("volunteer_lounge_post"));
    assertEquals("—", conversions.get("challenge_started"));
    assertEquals("—", conversions.get("challenge_completed"));
  }

  private static Map<String, Long> rowsById(Map<String, Long> snap) {
    return AdminMetricsResource.buildFunnelRows(snap).stream()
        .collect(
            Collectors.toMap(
                AdminMetricsResource.FunnelRow::id,
                AdminMetricsResource.FunnelRow::count,
                (a, b) -> a));
  }

  private static Map<String, String> conversionById(Map<String, Long> snap) {
    return AdminMetricsResource.buildFunnelRows(snap).stream()
        .collect(
            Collectors.toMap(
                AdminMetricsResource.FunnelRow::id,
                AdminMetricsResource.FunnelRow::conversion,
                (a, b) -> a));
  }
}
