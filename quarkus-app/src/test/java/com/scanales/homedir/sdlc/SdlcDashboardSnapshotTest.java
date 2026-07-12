package com.scanales.homedir.sdlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SdlcDashboardSnapshotTest {

  @Test
  void servesOneInMemoryProjectionAndPreservesItAfterRefreshFailure() {
    SdlcObservabilityService source = mock(SdlcObservabilityService.class);
    when(source.status()).thenReturn(Map.of("worker", Map.of("state", "idle")));
    when(source.pipeline()).thenReturn(List.of());
    when(source.issues()).thenReturn(List.of(Map.of("number", 42)));
    when(source.prs()).thenReturn(List.of());
    when(source.metrics(7)).thenReturn(Map.of("rangeDays", 7));
    when(source.metrics(30)).thenReturn(Map.of("rangeDays", 30));
    when(source.metrics(90)).thenReturn(Map.of("rangeDays", 90));
    when(source.anomalies()).thenReturn(List.of());
    when(source.configuration()).thenReturn(Map.of("controlsEnabled", false));

    SdlcDashboardSnapshot snapshot = new SdlcDashboardSnapshot(source);
    snapshot.refresh();

    Map<String, Object> first = snapshot.get();
    assertFalse((Boolean) first.get("stale"));
    assertEquals(1, ((List<?>) first.get("issues")).size());

    when(source.status()).thenThrow(new IllegalStateException("state file changed mid-read"));
    snapshot.refresh();

    assertEquals(first, snapshot.get());
  }
}
