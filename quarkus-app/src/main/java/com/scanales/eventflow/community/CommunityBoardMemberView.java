package com.scanales.eventflow.community;

public record CommunityBoardMemberView(
    String id,
    String displayName,
    String handle,
    String avatarUrl,
    String memberSince,
    String profileLink,
    String shareLink) {}
