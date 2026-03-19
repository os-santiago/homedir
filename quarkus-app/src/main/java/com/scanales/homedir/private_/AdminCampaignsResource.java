package com.scanales.homedir.private_;

import com.scanales.homedir.campaigns.CampaignService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Locale;
import java.util.ResourceBundle;

/** Hidden admin surface for internal marketing campaign drafts. */
@Path("/private/admin/campaigns")
public class AdminCampaignsResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(AdminCampaignsCopy copy, CampaignService.CampaignPreviewSnapshot view, boolean refreshed);
  }

  @Inject SecurityIdentity identity;
  @Inject CampaignService campaignService;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response index(@Context HttpHeaders headers, @QueryParam("refreshed") String refreshed) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String localeCode = TemplateLocaleUtil.resolve(null, headers);
    TemplateInstance template =
        Templates.index(
            localizedCopy(localeCode),
            campaignService.preview(localeCode),
            "1".equals(refreshed));
    return Response.ok(TemplateLocaleUtil.apply(template, localeCode, headers)).build();
  }

  @POST
  @Path("refresh")
  @Authenticated
  public Response refresh() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.refreshDrafts();
    return Response.seeOther(URI.create("/private/admin/campaigns?refreshed=1")).build();
  }

  private AdminCampaignsCopy localizedCopy(String localeCode) {
    Locale locale = Locale.forLanguageTag("en".equalsIgnoreCase(localeCode) ? "en" : "es");
    ResourceBundle bundle = ResourceBundle.getBundle("i18n", locale);
    return new AdminCampaignsCopy(
        text(bundle, "campaigns_admin_page_title"),
        text(bundle, "campaigns_admin_subtitle"),
        text(bundle, "campaigns_admin_heading"),
        text(bundle, "campaigns_admin_intro"),
        text(bundle, "campaigns_admin_back_to_panel"),
        text(bundle, "campaigns_admin_refresh"),
        text(bundle, "campaigns_admin_refreshed"),
        text(bundle, "campaigns_admin_generated_at"),
        text(bundle, "campaigns_admin_guardrail_title"),
        text(bundle, "campaigns_admin_guardrail_intro"),
        java.util.List.of(
            text(bundle, "campaigns_admin_guardrail_1"),
            text(bundle, "campaigns_admin_guardrail_2"),
            text(bundle, "campaigns_admin_guardrail_3"),
            text(bundle, "campaigns_admin_guardrail_4")),
        text(bundle, "campaigns_admin_empty"),
        text(bundle, "campaigns_admin_channels"),
        text(bundle, "campaigns_admin_evidence"),
        text(bundle, "campaigns_admin_cta"),
        text(bundle, "campaigns_admin_unknown_cta"));
  }

  private static String text(ResourceBundle bundle, String key) {
    return bundle.containsKey(key) ? bundle.getString(key) : key;
  }

  public record AdminCampaignsCopy(
      String pageTitle,
      String subtitle,
      String heading,
      String intro,
      String backToPanel,
      String refresh,
      String refreshed,
      String generatedAt,
      String guardrailTitle,
      String guardrailIntro,
      java.util.List<String> guardrails,
      String emptyState,
      String channelsLabel,
      String evidenceLabel,
      String ctaLabel,
      String unknownCtaLabel) {}
}
