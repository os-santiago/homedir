package com.scanales.eventflow.community;

import java.time.Instant;

public record CommunityBoardSummary(
    int homedirUsers,
    int githubUsers,
    int discordUsers,
    int discordListedUsers,
    int discordLinkedProfiles,
    int discordCoveragePercent,
    Integer discordOnlineUsers,
    String discordDataSource,
    Instant discordLastSyncAt) {}
