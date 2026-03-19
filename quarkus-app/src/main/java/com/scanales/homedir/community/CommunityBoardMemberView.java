package com.scanales.homedir.community;

public record CommunityBoardMemberView(
    String id,
    String displayName,
    String handle,
    String avatarUrl,
    String memberSince,
    String profileLink,
    String shareLink) {}
