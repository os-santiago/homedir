package com.scanales.homedir.community;

public record CommunityVoteAggregate(
    long recommended,
    long mustSee,
    long notForMe,
    CommunityVoteType myVote) {

  public static CommunityVoteAggregate empty() {
    return new CommunityVoteAggregate(0L, 0L, 0L, null);
  }
}

