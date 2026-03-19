package com.scanales.homedir.cfp;

import java.util.List;

public record CfpTimelineView(
    String eventId,
    String eventTitle,
    String fromLabel,
    String toLabel,
    List<CfpTimelineStageView> stages) {
}

