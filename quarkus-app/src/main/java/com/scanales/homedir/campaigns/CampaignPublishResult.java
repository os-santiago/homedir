package com.scanales.homedir.campaigns;

import java.time.Instant;

public record CampaignPublishResult(
    boolean published,
    boolean skipped,
    String channel,
    Instant publishedAt,
    String outcome) {

  public static CampaignPublishResult published(String channel, Instant publishedAt, String outcome) {
    return new CampaignPublishResult(true, false, channel, publishedAt, outcome);
  }

  public static CampaignPublishResult skipped(String channel, String outcome) {
    return new CampaignPublishResult(false, true, channel, null, outcome);
  }

  public static CampaignPublishResult failed(String channel, String outcome) {
    return new CampaignPublishResult(false, false, channel, null, outcome);
  }
}
