package com.scanales.eventflow.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
            true, true, null, 21, "widget_api", List.of(), null, null, true);
    assertEquals(9, snapshot.resolveMemberCount(9));
  }

  @Test
  void parseMemberSamplesReadsWidgetMembers() throws Exception {
    var node =
        JSON.readTree(
            """
            {
              "members": [
                {
                  "id": "123",
                  "username": "alice",
                  "global_name": "Alice Dev",
                  "discriminator": "0001",
                  "avatar_url": "https://cdn.discordapp.com/a.png"
                }
              ]
            }
            """);
    var members = DiscordGuildStatsService.parseMemberSamples(node);
    assertEquals(1, members.size());
    var first = members.get(0);
    assertEquals("123", first.id());
    assertEquals("Alice Dev", first.displayName());
    assertEquals("alice#0001", first.handle());
    assertNotNull(first.avatarUrl());
  }

  @Test
  void parseMemberSamplesReturnsEmptyWhenMembersMissing() throws Exception {
    var node = JSON.readTree("{\"approximate_member_count\":220}");
    assertTrue(DiscordGuildStatsService.parseMemberSamples(node).isEmpty());
  }
}
