package com.scanales.eventflow.community;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

