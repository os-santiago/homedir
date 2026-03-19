package com.scanales.homedir.private_;

import com.scanales.homedir.campaigns.CampaignService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.ResourceBundle;

/** Hidden admin surface for internal marketing campaign drafts. */
@Path("/private/admin/campaigns")
public class AdminCampaignsResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        AdminCampaignsCopy copy,
        CampaignService.CampaignPreviewSnapshot view,
        boolean refreshed,
        String updatedAction,
        String updatedDraft,
        String errorCode);
  }

  @Inject SecurityIdentity identity;
  @Inject CampaignService campaignService;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response index(
      @Context HttpHeaders headers,
      @QueryParam("refreshed") String refreshed,
      @QueryParam("updated") String updated,
      @QueryParam("draft") String draftId,
      @QueryParam("error") String errorCode) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String localeCode = TemplateLocaleUtil.resolve(null, headers);
    TemplateInstance template =
        Templates.index(
            localizedCopy(localeCode),
            campaignService.preview(localeCode),
            "1".equals(refreshed),
            safe(updated),
            safe(draftId),
            safe(errorCode));
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

  @POST
  @Path("publish-now")
  @Authenticated
  public Response publishNow() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.publishScheduledNow();
    return Response.seeOther(URI.create("/private/admin/campaigns?updated=publishscan")).build();
  }

  @POST
  @Path("{draftId}/approve")
  @Authenticated
  public Response approve(@PathParam("draftId") String draftId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.approveDraft(draftId, identity.getPrincipal().getName());
    return redirectWithUpdate("approved", draftId);
  }

  @POST
  @Path("{draftId}/reset")
  @Authenticated
  public Response reset(@PathParam("draftId") String draftId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.resetDraft(draftId);
    return redirectWithUpdate("reset", draftId);
  }

  @POST
  @Path("{draftId}/schedule")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Authenticated
  public Response schedule(@PathParam("draftId") String draftId, @FormParam("scheduledFor") String scheduledFor) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (scheduledFor == null || scheduledFor.isBlank()) {
      return redirectWithError("invalid_schedule", draftId);
    }
    try {
      campaignService.scheduleDraft(draftId, LocalDateTime.parse(scheduledFor), identity.getPrincipal().getName());
      return redirectWithUpdate("scheduled", draftId);
    } catch (Exception e) {
      return redirectWithError("invalid_schedule", draftId);
    }
  }

  @POST
  @Path("{draftId}/unschedule")
  @Authenticated
  public Response unschedule(@PathParam("draftId") String draftId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.unscheduleDraft(draftId);
    return redirectWithUpdate("unscheduled", draftId);
  }

  @POST
  @Path("{draftId}/mark-linkedin")
  @Authenticated
  public Response markLinkedin(@PathParam("draftId") String draftId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.markLinkedinPublished(draftId, identity.getPrincipal().getName());
    return redirectWithUpdate("linkedin", draftId);
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
        text(bundle, "campaigns_admin_updated_approved"),
        text(bundle, "campaigns_admin_updated_reset"),
        text(bundle, "campaigns_admin_updated_scheduled"),
        text(bundle, "campaigns_admin_updated_unscheduled"),
        text(bundle, "campaigns_admin_updated_publishscan"),
        text(bundle, "campaigns_admin_updated_linkedin"),
        text(bundle, "campaigns_admin_error_invalid_schedule"),
        text(bundle, "campaigns_admin_generated_at"),
        text(bundle, "campaigns_admin_summary_title"),
        text(bundle, "campaigns_admin_summary_intro"),
        text(bundle, "campaigns_admin_summary_total"),
        text(bundle, "campaigns_admin_summary_draft"),
        text(bundle, "campaigns_admin_summary_approved"),
        text(bundle, "campaigns_admin_summary_scheduled"),
        text(bundle, "campaigns_admin_summary_published"),
        text(bundle, "campaigns_admin_summary_linkedin_pending"),
        text(bundle, "campaigns_admin_summary_linkedin_done"),
        text(bundle, "campaigns_admin_summary_last_published"),
        text(bundle, "campaigns_admin_cadence_title"),
        text(bundle, "campaigns_admin_cadence_intro"),
        text(bundle, "campaigns_admin_cadence_overall"),
        text(bundle, "campaigns_admin_cadence_by_kind"),
        text(bundle, "campaigns_admin_cadence_best_window"),
        text(bundle, "campaigns_admin_cadence_no_window"),
        text(bundle, "campaigns_admin_recent_activity_title"),
        text(bundle, "campaigns_admin_recent_activity_intro"),
        text(bundle, "campaigns_admin_recent_activity_updated"),
        text(bundle, "campaigns_admin_preview_packs_title"),
        text(bundle, "campaigns_admin_preview_packs_intro"),
        text(bundle, "campaigns_admin_preview_headline"),
        text(bundle, "campaigns_admin_preview_message"),
        text(bundle, "campaigns_admin_preview_length_label"),
        text(bundle, "campaigns_admin_preview_status_label"),
        text(bundle, "campaigns_admin_linkedin_title"),
        text(bundle, "campaigns_admin_linkedin_intro"),
        text(bundle, "campaigns_admin_linkedin_headline_label"),
        text(bundle, "campaigns_admin_linkedin_message_label"),
        text(bundle, "campaigns_admin_linkedin_landing_label"),
        text(bundle, "campaigns_admin_linkedin_pending"),
        text(bundle, "campaigns_admin_linkedin_done"),
        text(bundle, "campaigns_admin_linkedin_empty"),
        text(bundle, "campaigns_admin_guardrail_title"),
        text(bundle, "campaigns_admin_guardrail_intro"),
        java.util.List.of(
            text(bundle, "campaigns_admin_guardrail_1"),
            text(bundle, "campaigns_admin_guardrail_2"),
            text(bundle, "campaigns_admin_guardrail_3"),
            text(bundle, "campaigns_admin_guardrail_4")),
        text(bundle, "campaigns_admin_empty"),
        text(bundle, "campaigns_admin_review_queue_title"),
        text(bundle, "campaigns_admin_approved_queue_title"),
        text(bundle, "campaigns_admin_scheduled_queue_title"),
        text(bundle, "campaigns_admin_published_queue_title"),
        text(bundle, "campaigns_admin_queue_intro"),
        text(bundle, "campaigns_admin_channels"),
        text(bundle, "campaigns_admin_evidence"),
        text(bundle, "campaigns_admin_cta"),
        text(bundle, "campaigns_admin_unknown_cta"),
        text(bundle, "campaigns_admin_workflow"),
        text(bundle, "campaigns_admin_source"),
        text(bundle, "campaigns_admin_schedule_for"),
        text(bundle, "campaigns_admin_best_window"),
        text(bundle, "campaigns_admin_published_channels"),
        text(bundle, "campaigns_admin_publisher_status"),
        text(bundle, "campaigns_admin_publisher_intro"),
        text(bundle, "campaigns_admin_publisher_global"),
        text(bundle, "campaigns_admin_publisher_dry_run"),
        text(bundle, "campaigns_admin_publisher_channel"),
        text(bundle, "campaigns_admin_publisher_webhook"),
        text(bundle, "campaigns_admin_publisher_rate_limit"),
        text(bundle, "campaigns_admin_publisher_run"),
        text(bundle, "campaigns_admin_btn_approve"),
        text(bundle, "campaigns_admin_btn_reset"),
        text(bundle, "campaigns_admin_btn_schedule"),
        text(bundle, "campaigns_admin_btn_unschedule"),
        text(bundle, "campaigns_admin_btn_mark_linkedin"));
  }

  private static String text(ResourceBundle bundle, String key) {
    return bundle.containsKey(key) ? bundle.getString(key) : key;
  }

  private Response redirectWithUpdate(String update, String draftId) {
    return Response.seeOther(URI.create("/private/admin/campaigns?updated=" + safe(update) + "&draft=" + safe(draftId))).build();
  }

  private Response redirectWithError(String error, String draftId) {
    return Response.seeOther(URI.create("/private/admin/campaigns?error=" + safe(error) + "&draft=" + safe(draftId))).build();
  }

  private static String safe(String raw) {
    return raw == null ? "" : raw.replaceAll("[^a-zA-Z0-9\\-_]", "");
  }

  public record AdminCampaignsCopy(
      String pageTitle,
      String subtitle,
      String heading,
      String intro,
      String backToPanel,
      String refresh,
      String refreshed,
      String updatedApproved,
      String updatedReset,
      String updatedScheduled,
      String updatedUnscheduled,
      String updatedPublishScan,
      String updatedLinkedin,
      String invalidSchedule,
      String generatedAt,
      String summaryTitle,
      String summaryIntro,
      String summaryTotalLabel,
      String summaryDraftLabel,
      String summaryApprovedLabel,
      String summaryScheduledLabel,
      String summaryPublishedLabel,
      String summaryLinkedinPendingLabel,
      String summaryLinkedinDoneLabel,
      String summaryLastPublishedLabel,
      String cadenceTitle,
      String cadenceIntro,
      String cadenceOverallLabel,
      String cadenceByKindLabel,
      String cadenceBestWindowLabel,
      String cadenceNoWindowLabel,
      String recentActivityTitle,
      String recentActivityIntro,
      String recentActivityUpdatedLabel,
      String previewPacksTitle,
      String previewPacksIntro,
      String previewHeadlineLabel,
      String previewMessageLabel,
      String previewLengthLabel,
      String previewStatusLabel,
      String linkedinTitle,
      String linkedinIntro,
      String linkedinHeadlineLabel,
      String linkedinMessageLabel,
      String linkedinLandingLabel,
      String linkedinPendingLabel,
      String linkedinDoneLabel,
      String linkedinEmptyLabel,
      String guardrailTitle,
      String guardrailIntro,
      java.util.List<String> guardrails,
      String emptyState,
      String reviewQueueTitle,
      String approvedQueueTitle,
      String scheduledQueueTitle,
      String publishedQueueTitle,
      String queueIntro,
      String channelsLabel,
      String evidenceLabel,
      String ctaLabel,
      String unknownCtaLabel,
      String workflowLabel,
      String sourceLabel,
      String scheduleForLabel,
      String bestWindowLabel,
      String publishedChannelsLabel,
      String publisherStatusLabel,
      String publisherIntro,
      String publisherGlobalLabel,
      String publisherDryRunLabel,
      String publisherChannelLabel,
      String publisherConfiguredLabel,
      String publisherRateLimitLabel,
      String publisherRunLabel,
      String approveLabel,
      String resetLabel,
      String scheduleLabel,
      String unscheduleLabel,
      String markLinkedinLabel) {}
}
