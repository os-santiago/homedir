package com.scanales.eventflow.community;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommunityVoteService {
  private static final Logger LOG = Logger.getLogger(CommunityVoteService.class);

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @ConfigProperty(name = "community.votes.db.path")
  Optional<String> dbPathConfig;

  @ConfigProperty(name = "community.votes.daily-limit", defaultValue = "100")
  int dailyLimit;

  private String jdbcUrl;

  @PostConstruct
  void init() {
    Path dbPath = resolveDbPath();
    try {
      Files.createDirectories(dbPath.getParent());
    } catch (Exception e) {
      throw new IllegalStateException("Unable to create votes DB directory: " + dbPath, e);
    }
    String path = dbPath.toAbsolutePath().toString().replace("\\", "/");
    jdbcUrl = "jdbc:h2:file:" + path;
    initSchema();
    LOG.infov("Community votes DB initialized at {0}", dbPath.toAbsolutePath());
  }

  public void upsertVote(String userId, String contentId, CommunityVoteType vote) {
    Instant now = Instant.now();
    try (Connection conn = connection()) {
      conn.setAutoCommit(false);
      try {
        boolean exists = findVote(conn, userId, contentId).isPresent();
        if (!exists && isRateLimited(conn, userId, now)) {
          throw new RateLimitExceededException("daily_vote_limit_reached");
        }
        if (exists) {
          try (PreparedStatement ps =
              conn.prepareStatement(
                  "UPDATE content_vote SET vote = ?, updated_at = ? WHERE user_id = ? AND content_id = ?")) {
            ps.setString(1, vote.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setString(3, userId);
            ps.setString(4, contentId);
            ps.executeUpdate();
          }
        } else {
          try (PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO content_vote (user_id, content_id, vote, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, contentId);
            ps.setString(3, vote.name());
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();
          }
        }
        conn.commit();
      } catch (RateLimitExceededException e) {
        conn.rollback();
        throw e;
      } catch (Exception e) {
        conn.rollback();
        throw e;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (RateLimitExceededException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to persist vote", e);
    }
  }

  public Map<String, CommunityVoteAggregate> getAggregates(Collection<String> contentIds, String userId) {
    Map<String, AggregateBuilder> builders = new HashMap<>();
    for (String id : contentIds) {
      builders.put(id, new AggregateBuilder());
    }
    if (builders.isEmpty()) {
      return Map.of();
    }
    try (Connection conn = connection()) {
      String inClause = inClause(builders.size());
      try (PreparedStatement ps =
          conn.prepareStatement(
              "SELECT content_id, vote, COUNT(*) AS total "
                  + "FROM content_vote WHERE content_id IN ("
                  + inClause
                  + ") GROUP BY content_id, vote")) {
        bindIds(ps, builders.keySet());
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            String contentId = rs.getString("content_id");
            String vote = rs.getString("vote");
            long total = rs.getLong("total");
            AggregateBuilder builder = builders.get(contentId);
            if (builder != null) {
              builder.addCount(vote, total);
            }
          }
        }
      }
      if (userId != null && !userId.isBlank()) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "SELECT content_id, vote FROM content_vote WHERE user_id = ? AND content_id IN ("
                    + inClause(builders.size())
                    + ")")) {
          ps.setString(1, userId);
          bindIds(ps, builders.keySet(), 2);
          try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              String contentId = rs.getString("content_id");
              String rawVote = rs.getString("vote");
              AggregateBuilder builder = builders.get(contentId);
              if (builder != null) {
                CommunityVoteType.fromApi(rawVote.toLowerCase(Locale.ROOT))
                    .ifPresent(builder::setMyVote);
              }
            }
          }
        }
      }
      Map<String, CommunityVoteAggregate> out = new HashMap<>();
      builders.forEach((id, b) -> out.put(id, b.toAggregate()));
      return out;
    } catch (Exception e) {
      LOG.error("Unable to read vote aggregates", e);
      Map<String, CommunityVoteAggregate> fallback = new HashMap<>();
      builders.keySet().forEach(id -> fallback.put(id, CommunityVoteAggregate.empty()));
      return fallback;
    }
  }

  public void clearAllForTests() {
    try (Connection conn = connection();
        PreparedStatement ps = conn.prepareStatement("DELETE FROM content_vote")) {
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to clear votes", e);
    }
  }

  private Path resolveDbPath() {
    String configured = dbPathConfig.orElse("").trim();
    if (!configured.isEmpty()) {
      return Paths.get(configured);
    }
    String sysProp = System.getProperty("homedir.data.dir");
    String base = (sysProp != null && !sysProp.isBlank()) ? sysProp : dataDirPath;
    return Paths.get(base, "community", "votes", "community-votes");
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(jdbcUrl, "sa", "");
  }

  private void initSchema() {
    try (Connection conn = connection();
        PreparedStatement table =
            conn.prepareStatement(
                """
                CREATE TABLE IF NOT EXISTS content_vote (
                  user_id VARCHAR(320) NOT NULL,
                  content_id VARCHAR(200) NOT NULL,
                  vote VARCHAR(20) NOT NULL,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL,
                  PRIMARY KEY (user_id, content_id)
                )
                """);
        PreparedStatement idx1 =
            conn.prepareStatement(
                "CREATE INDEX IF NOT EXISTS idx_content_vote_content_id ON content_vote(content_id)");
        PreparedStatement idx2 =
            conn.prepareStatement(
                "CREATE INDEX IF NOT EXISTS idx_content_vote_user_updated ON content_vote(user_id, updated_at)")) {
      table.execute();
      idx1.execute();
      idx2.execute();
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to initialize content_vote schema", e);
    }
  }

  private Optional<CommunityVoteType> findVote(Connection conn, String userId, String contentId)
      throws SQLException {
    try (PreparedStatement ps =
        conn.prepareStatement("SELECT vote FROM content_vote WHERE user_id = ? AND content_id = ?")) {
      ps.setString(1, userId);
      ps.setString(2, contentId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        String raw = rs.getString("vote");
        return CommunityVoteType.fromApi(raw.toLowerCase(Locale.ROOT));
      }
    }
  }

  private boolean isRateLimited(Connection conn, String userId, Instant now) throws SQLException {
    if (dailyLimit <= 0) {
      return false;
    }
    Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT COUNT(*) AS total FROM content_vote WHERE user_id = ? AND updated_at >= ?")) {
      ps.setString(1, userId);
      ps.setTimestamp(2, Timestamp.from(dayStart));
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return false;
        }
        long total = rs.getLong("total");
        return total >= dailyLimit;
      }
    }
  }

  private static String inClause(int size) {
    StringJoiner joiner = new StringJoiner(",");
    for (int i = 0; i < size; i++) {
      joiner.add("?");
    }
    return joiner.toString();
  }

  private static void bindIds(PreparedStatement ps, Collection<String> ids) throws SQLException {
    bindIds(ps, ids, 1);
  }

  private static void bindIds(PreparedStatement ps, Collection<String> ids, int startAt)
      throws SQLException {
    int idx = startAt;
    for (String id : ids) {
      ps.setString(idx++, id);
    }
  }

  private static final class AggregateBuilder {
    long recommended;
    long mustSee;
    long notForMe;
    CommunityVoteType myVote;

    void addCount(String rawVote, long total) {
      if (rawVote == null) {
        return;
      }
      switch (rawVote) {
        case "RECOMMENDED" -> recommended += total;
        case "MUST_SEE" -> mustSee += total;
        case "NOT_FOR_ME" -> notForMe += total;
        default -> {
        }
      }
    }

    void setMyVote(CommunityVoteType voteType) {
      myVote = voteType;
    }

    CommunityVoteAggregate toAggregate() {
      return new CommunityVoteAggregate(recommended, mustSee, notForMe, myVote);
    }
  }

  public static class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
      super(message);
    }
  }
}
