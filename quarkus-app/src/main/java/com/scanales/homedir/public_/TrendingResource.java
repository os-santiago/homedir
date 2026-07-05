package com.scanales.homedir.public_;

import com.scanales.homedir.trending.TrendingPeriod;
import com.scanales.homedir.trending.TrendingRepo;
import com.scanales.homedir.trending.TrendingService;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Path("/trending")
public class TrendingResource {

  private static final Logger LOG = Logger.getLogger(TrendingResource.class);
  private static final int DEFAULT_COUNT = 1;

  @ConfigProperty(name = "trending.max-count", defaultValue = "10")
  int maxCount;

  @Inject SecurityIdentity identity;

  @Inject TrendingService trendingService;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        List<TrendingRepo> repos, String period, int count, String lastUpdated);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance index(
      @QueryParam("period") String periodParam,
      @QueryParam("count") Integer countParam,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie) {

    TrendingPeriod period = TrendingPeriod.fromString(periodParam);
    int count = normalizeCount(countParam);

    List<TrendingRepo> repos = trendingService.getTrending(period, count);

    String lastUpdated = formatLastUpdated(repos);

    boolean authenticated = isAuthenticated();
    String name = currentUserName();

    TemplateInstance template = Templates.index(repos, period.toGithubPath(), count, lastUpdated);

    return TemplateLocaleUtil.apply(template, localeCookie)
        .data("activePage", "trending")
        .data("userAuthenticated", authenticated)
        .data("userName", name)
        .data("userInitial", initialFrom(name));
  }

  private int normalizeCount(Integer count) {
    if (count == null) {
      return DEFAULT_COUNT;
    }
    return Math.max(1, Math.min(count, maxCount));
  }

  private String formatLastUpdated(List<TrendingRepo> repos) {
    if (repos.isEmpty()) {
      return null;
    }
    return java.time.Instant.now().toString();
  }

  private boolean isAuthenticated() {
    try {
      return identity != null && !identity.isAnonymous();
    } catch (Exception e) {
      LOG.warn("Security identity check failed (treating as anonymous): " + e.getMessage());
      return false;
    }
  }

  private String currentUserName() {
    if (!isAuthenticated()) {
      return null;
    }
    String name = identity.getAttribute("name");
    if (name == null || name.isBlank()) {
      name = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    }
    return name;
  }

  private String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase();
  }
}
