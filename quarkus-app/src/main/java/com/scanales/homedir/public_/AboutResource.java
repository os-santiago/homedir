package com.scanales.homedir.public_;

import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.eclipse.microprofile.config.ConfigProvider;

@Path("/about")
@PermitAll
public class AboutResource {

  @Inject SecurityIdentity identity;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance about(
        String version,
        String commitId,
        String buildTime,
        String environment,
        boolean oidcConfigured,
        boolean githubConfigured,
        boolean isAdmin);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance get(@jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie) {
    boolean isAdmin = AdminUtils.isAdmin(identity);

    String version =
        ConfigProvider.getConfig()
            .getOptionalValue("quarkus.application.version", String.class)
            .orElse("unknown");

    Properties gitProps = new Properties();
    try (InputStream is = AboutResource.class.getResourceAsStream("/git.properties")) {
      if (is != null) {
        gitProps.load(is);
      }
    } catch (IOException e) {
      // Ignore
    }

    String commitId = isAdmin ? gitProps.getProperty("git.commit.id.abbrev", "dev") : "";
    String buildTime = isAdmin ? gitProps.getProperty("git.build.time", "now") : "";
    String environment =
        isAdmin
            ? ConfigProvider.getConfig()
                .getOptionalValue("quarkus.profile", String.class)
                .orElse("dev")
            : "";

    String oidcClientId =
        ConfigProvider.getConfig()
            .getOptionalValue("quarkus.oidc.client-id", String.class)
            .orElse("missing");
    boolean oidcConfigured = !"dev-client".equals(oidcClientId) && !"missing".equals(oidcClientId);

    String ghClientId =
        ConfigProvider.getConfig().getOptionalValue("GH_CLIENT_ID", String.class).orElse("missing");
    boolean githubConfigured =
        ghClientId != null && !ghClientId.isEmpty() && !"missing".equals(ghClientId);

    return TemplateLocaleUtil.apply(
        Templates.about(
            version, commitId, buildTime, environment, oidcConfigured, githubConfigured, isAdmin),
        localeCookie);
  }
}
