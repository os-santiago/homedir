package com.scanales.eventflow.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DiscordGuildStatsServiceTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void firstIntReadsNumericFields() throws Exception {
    var node = JSON.readTree("{\"approximate_member_count\":1234,\"presence_count\":77}");
    assertEquals(1234, DiscordGuildStatsService.firstInt(node, "approximate_member_count", "member_count"));
    assertEquals(77, DiscordGuildStatsService.firstInt(node, "approximate_presence_count", "presence_count"));
  }

  @Test
  void firstIntReadsNumericStringFields() throws Exception {
    var node = JSON.readTree("{\"member_count\":\"42\"}");
    assertEquals(42, DiscordGuildStatsService.firstInt(node, "approximate_member_count", "member_count"));
  }

  @Test
  void firstIntReturnsNullForMissingValues() throws Exception {
    var node = JSON.readTree("{\"name\":\"ossantiago\"}");
    assertNull(DiscordGuildStatsService.firstInt(node, "approximate_member_count", "member_count"));
  }

  @Test
  void snapshotResolveMemberCountFallsBackToFileCount() {
    var snapshot =
        new DiscordGuildStatsService.DiscordGuildSnapshot(
            true, true, null, 21, "widget_api", null, null, true);
    assertEquals(9, snapshot.resolveMemberCount(9));
  }
}
