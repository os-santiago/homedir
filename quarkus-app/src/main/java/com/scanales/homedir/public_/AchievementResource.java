package com.scanales.homedir.public_;

import com.scanales.homedir.achievements.AchievementCatalog;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Phase 1 of the GitHub Achievement Hub (issue #1323, parent #1043).
 *
 * <p>Renders a read-only dashboard of the GitHub achievements the os-santiago community can work
 * toward, with the org repositories that help earn each one. Real per-user achievement state via
 * the GitHub API is a later phase; here every achievement is shown as locked.
 */
@Path("/achievements")
public class AchievementResource {

  private static final Logger LOG = Logger.getLogger(AchievementResource.class);

  @Inject SecurityIdentity identity;

  @Inject AchievementCatalog catalog;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        List<AchievementCatalog.AchievementGuide> guides,
        List<AchievementCatalog.OrgRepo> orgRepos);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance index(@CookieParam("QP_LOCALE") String localeCookie) {
    boolean authenticated = isAuthenticated();
    String name = currentUserName();

    TemplateInstance template = Templates.index(catalog.guides(), catalog.orgRepos());

    return TemplateLocaleUtil.apply(template, localeCookie)
        .data("activePage", "achievements")
        .data("userAuthenticated", authenticated)
        .data("userName", name)
        .data("userInitial", initialFrom(name));
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
