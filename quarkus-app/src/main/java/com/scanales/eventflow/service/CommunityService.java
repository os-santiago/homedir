package com.scanales.eventflow.service;

import com.scanales.eventflow.model.CommunityMember;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class CommunityService {

  @Inject
  CommunitySyncService sync;

  public List<CommunityMember> listMembers() {
    return sync.fetchMembers();
  }

  public List<CommunityMember> search(String query) {
    List<CommunityMember> all = listMembers();
    if (query == null || query.isBlank()) {
      return all;
    }
    String q = query.toLowerCase(Locale.ROOT).trim();
    return all.stream()
        .filter(
            m -> (m.getDisplayName() != null
                && m.getDisplayName().toLowerCase(Locale.ROOT).contains(q))
                || (m.getGithub() != null
                    && m.getGithub().toLowerCase(Locale.ROOT).contains(q)))
        .collect(Collectors.toList());
  }

  public Optional<CommunityMember> findByGithub(String login) {
    if (login == null || login.isBlank()) {
      return Optional.empty();
    }
    String normalized = login.toLowerCase(Locale.ROOT);
    return listMembers().stream()
        .filter(m -> m.getGithub() != null && normalized.equalsIgnoreCase(m.getGithub()))
        .findFirst();
  }

  public Optional<CommunityMember> findByUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return Optional.empty();
    }
    String normalized = userId.toLowerCase(Locale.ROOT);
    return listMembers().stream()
        .filter(m -> m.getUserId() != null && normalized.equalsIgnoreCase(m.getUserId()))
        .findFirst();
  }

  public long countMembers() {
    return listMembers().size();
  }

  public Optional<String> requestJoin(CommunityMember member) {
    if (member == null) {
      return Optional.empty();
    }
    return sync.createMemberPullRequest(member);
  }

  public boolean hasPendingJoinRequest(String login) {
    return sync.hasPendingJoinRequest(login);
  }
}
