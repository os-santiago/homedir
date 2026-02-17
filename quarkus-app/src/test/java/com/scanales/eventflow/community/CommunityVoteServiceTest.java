package com.scanales.eventflow.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CommunityVoteServiceTest {

  @Inject CommunityVoteService voteService;

  @BeforeEach
  void setup() {
    voteService.clearAllForTests();
  }

  @Test
  void replacesExistingVoteForSameUserAndContent() {
    String contentId = "item-replace-1";
    String userId = "voter@example.com";

    voteService.upsertVote(userId, contentId, CommunityVoteType.RECOMMENDED);
    voteService.upsertVote(userId, contentId, CommunityVoteType.MUST_SEE);

    CommunityVoteAggregate aggregate =
        voteService.getAggregates(List.of(contentId), userId).get(contentId);

    assertEquals(0L, aggregate.recommended());
    assertEquals(1L, aggregate.mustSee());
    assertEquals(0L, aggregate.notForMe());
    assertEquals(CommunityVoteType.MUST_SEE, aggregate.myVote());
  }

  @Test
  void anonymousAggregatesReflectLatestVotesAfterUpsert() {
    String contentId = "item-cache-1";

    voteService.upsertVote("a@example.com", contentId, CommunityVoteType.RECOMMENDED);
    CommunityVoteAggregate first = voteService.getAggregates(List.of(contentId), null).get(contentId);
    assertNotNull(first);
    assertEquals(1L, first.recommended());
    assertEquals(0L, first.mustSee());

    voteService.upsertVote("b@example.com", contentId, CommunityVoteType.MUST_SEE);
    CommunityVoteAggregate second = voteService.getAggregates(List.of(contentId), null).get(contentId);
    assertNotNull(second);
    assertEquals(1L, second.recommended());
    assertEquals(1L, second.mustSee());
  }
}
