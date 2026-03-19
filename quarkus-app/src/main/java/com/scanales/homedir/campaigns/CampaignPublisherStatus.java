package com.scanales.homedir.campaigns;

import java.time.Duration;

public record CampaignPublisherStatus(
    String channel,
    boolean globalEnabled,
    boolean dryRun,
    boolean channelEnabled,
    boolean configured,
    Duration minInterval) {}
