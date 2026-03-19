package com.scanales.homedir.cfp;

public record CfpTimelineStageView(
    String key,
    int flexDays,
    String fromLabel,
    String toLabel,
    boolean active) {
}

