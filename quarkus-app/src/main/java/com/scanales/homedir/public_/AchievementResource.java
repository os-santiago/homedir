package com.scanales.homedir.public_;

import com.scanales.homedir.achievements.AchievementCatalog;
import com.scanales.homedir.achievements.AchievementCatalog.AchievementGuide;
import com.scanales.homedir.achievements.AchievementView;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

/**
 * GitHub Achievement Hub (issue #1043, Phase 1).
 *
 * <p>Displays a read-only catalog of GitHub achievements with bilingual step-by-step guides and the
 * os-santiago repositories that help earn each one. Per-user progress verification, XP awards, and
 * the community leaderboard are deferred to Phase 2+.
 */
@Path("/achievements")
public class AchievementResource {

  @Inject AchievementCatalog catalog;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        List<AchievementView> achievements, List<AchievementCatalog.OrgRepo> orgRepos);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance index(@CookieParam("QP_LOCALE") String localeCookie) {
    List<AchievementView> views = new ArrayList<>();
    for (AchievementGuide guide : catalog.guides()) {
      views.add(AchievementView.from(guide, localeCookie));
    }

    TemplateInstance template = Templates.index(views, catalog.orgRepos());
    return TemplateLocaleUtil.apply(template, localeCookie).data("activePage", "achievements");
  }
}
