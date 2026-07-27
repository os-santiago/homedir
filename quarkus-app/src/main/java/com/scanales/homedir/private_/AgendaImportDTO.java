package com.scanales.homedir.private_;

import java.util.List;

/**
 * DTO for agenda import containing scenarios, talks, and breaks.
 */
public record AgendaImportDTO(
    List<ScenarioDTO> scenarios,
    List<TalkDTO> talks,
    List<BreakDTO> breaks) {

  public record ScenarioDTO(String id, String name, String features, String location) {}

  public record TalkDTO(
      String id,
      String name,
      String speakerId,
      String speakerName,
      String scenarioId,
      String startTime,
      int day,
      int durationMinutes,
      String description) {}

  public record BreakDTO(
      String id, String name, int durationMinutes, String scenarioId, String startTime, int day) {}
}
