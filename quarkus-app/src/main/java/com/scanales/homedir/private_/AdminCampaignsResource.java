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
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import jakarta.ws.rs.core.UriBuilder;

/** Hidden admin surface for internal marketing campaign drafts. */
@Path("/private/admin/campaigns")
public class AdminCampaignsResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        AdminCampaignsCopy copy,
        AdminCampaignFilters filters,
        List<AdminFilterOption> workflowOptions,
        List<AdminFilterOption> kindOptions,
        List<AdminFilterOption> channelOptions,
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
      @QueryParam("q") String query,
      @QueryParam("workflow") String workflow,
      @QueryParam("kind") String kind,
      @QueryParam("channel") String channel,
      @QueryParam("refreshed") String refreshed,
      @QueryParam("updated") String updated,
      @QueryParam("draft") String draftId,
      @QueryParam("error") String errorCode) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String localeCode = TemplateLocaleUtil.resolve(null, headers);
    AdminCampaignFilters filters = AdminCampaignFilters.sanitize(query, workflow, kind, channel);
    TemplateInstance template =
        Templates.index(
            localizedCopy(localeCode),
            filters,
            workflowOptions(localeCode),
            kindOptions(localeCode),
            channelOptions(localeCode),
            campaignService.preview(
                localeCode,
                new CampaignService.CampaignAdminFilters(
                    filters.query(), filters.workflow(), filters.kind(), filters.channel())),
            "1".equals(refreshed),
            safe(updated),
            safe(draftId),
            safe(errorCode));
    return Response.ok(TemplateLocaleUtil.apply(template, localeCode, headers)).build();
  }

  @POST
  @Path("refresh")
  @Authenticated
  public Response refresh(
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.refreshDrafts();
    return redirectWithUpdate("refresh", "", AdminCampaignFilters.sanitize(query, workflow, kind, channel), "refreshed", "1");
  }

  @POST
  @Path("publish-now")
  @Authenticated
  public Response publishNow(
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.publishScheduledNow();
    return redirectWithUpdate(
        "publishscan", "", AdminCampaignFilters.sanitize(query, workflow, kind, channel), null, null);
  }

  @POST
  @Path("{draftId}/approve")
  @Authenticated
  public Response approve(
      @PathParam("draftId") String draftId,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.approveDraft(draftId, identity.getPrincipal().getName());
    return redirectWithUpdate("approved", draftId, AdminCampaignFilters.sanitize(query, workflow, kind, channel), null, null);
  }

  @POST
  @Path("{draftId}/reset")
  @Authenticated
  public Response reset(
      @PathParam("draftId") String draftId,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.resetDraft(draftId);
    return redirectWithUpdate("reset", draftId, AdminCampaignFilters.sanitize(query, workflow, kind, channel), null, null);
  }

  @POST
  @Path("{draftId}/schedule")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Authenticated
  public Response schedule(
      @PathParam("draftId") String draftId,
      @FormParam("scheduledFor") String scheduledFor,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    AdminCampaignFilters filters = AdminCampaignFilters.sanitize(query, workflow, kind, channel);
    if (scheduledFor == null || scheduledFor.isBlank()) {
      return redirectWithError("invalid_schedule", draftId, filters);
    }
    try {
      campaignService.scheduleDraft(draftId, LocalDateTime.parse(scheduledFor), identity.getPrincipal().getName());
      return redirectWithUpdate("scheduled", draftId, filters, null, null);
    } catch (Exception e) {
      return redirectWithError("invalid_schedule", draftId, filters);
    }
  }

  @POST
  @Path("{draftId}/unschedule")
  @Authenticated
  public Response unschedule(
      @PathParam("draftId") String draftId,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.unscheduleDraft(draftId);
    return redirectWithUpdate("unscheduled", draftId, AdminCampaignFilters.sanitize(query, workflow, kind, channel), null, null);
  }

  @POST
  @Path("{draftId}/mark-linkedin")
  @Authenticated
  public Response markLinkedin(
      @PathParam("draftId") String draftId,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.markLinkedinPublished(draftId, identity.getPrincipal().getName());
    return redirectWithUpdate("linkedin", draftId, AdminCampaignFilters.sanitize(query, workflow, kind, channel), null, null);
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
        text(bundle, "campaigns_admin_filters_title"),
        text(bundle, "campaigns_admin_filters_intro"),
        text(bundle, "campaigns_admin_filter_query"),
        text(bundle, "campaigns_admin_filter_query_placeholder"),
        text(bundle, "campaigns_admin_filter_workflow"),
        text(bundle, "campaigns_admin_filter_kind"),
        text(bundle, "campaigns_admin_filter_channel"),
        text(bundle, "campaigns_admin_filter_all"),
        text(bundle, "campaigns_admin_filter_apply"),
        text(bundle, "campaigns_admin_filter_clear"),
        text(bundle, "campaigns_admin_filter_results"),
        text(bundle, "campaigns_admin_filter_empty"),
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
        text(bundle, "campaigns_admin_queue_health_title"),
        text(bundle, "campaigns_admin_queue_health_intro"),
        text(bundle, "campaigns_admin_queue_health_status_label"),
        text(bundle, "campaigns_admin_queue_health_attention"),
        text(bundle, "campaigns_admin_queue_health_stale_drafts"),
        text(bundle, "campaigns_admin_queue_health_stale_approved"),
        text(bundle, "campaigns_admin_queue_health_overdue_scheduled"),
        text(bundle, "campaigns_admin_queue_health_blocked"),
        text(bundle, "campaigns_admin_queue_health_linkedin"),
        text(bundle, "campaigns_admin_queue_health_evaluated"),
        text(bundle, "campaigns_admin_queue_risks_title"),
        text(bundle, "campaigns_admin_queue_risks_intro"),
        text(bundle, "campaigns_admin_queue_risks_empty"),
        text(bundle, "campaigns_admin_queue_risk_age"),
        text(bundle, "campaigns_admin_queue_risk_action"),
        text(bundle, "campaigns_admin_audit_title"),
        text(bundle, "campaigns_admin_audit_intro"),
        text(bundle, "campaigns_admin_audit_event"),
        text(bundle, "campaigns_admin_audit_channel"),
        text(bundle, "campaigns_admin_audit_outcome"),
        text(bundle, "campaigns_admin_audit_actor"),
        text(bundle, "campaigns_admin_audit_empty"),
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
        text(bundle, "campaigns_admin_preview_landing"),
        text(bundle, "campaigns_admin_preview_length_label"),
        text(bundle, "campaigns_admin_preview_status_label"),
        text(bundle, "campaigns_admin_attribution_title"),
        text(bundle, "campaigns_admin_attribution_intro"),
        text(bundle, "campaigns_admin_attribution_total"),
        text(bundle, "campaigns_admin_attribution_empty"),
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

  private List<AdminFilterOption> workflowOptions(String localeCode) {
    ResourceBundle bundle = ResourceBundle.getBundle("i18n", Locale.forLanguageTag("en".equalsIgnoreCase(localeCode) ? "en" : "es"));
    return List.of(
        new AdminFilterOption("draft", text(bundle, "campaigns_workflow_draft")),
        new AdminFilterOption("approved", text(bundle, "campaigns_workflow_approved")),
        new AdminFilterOption("scheduled", text(bundle, "campaigns_workflow_scheduled")),
        new AdminFilterOption("published", text(bundle, "campaigns_workflow_published")));
  }

  private List<AdminFilterOption> kindOptions(String localeCode) {
    ResourceBundle bundle = ResourceBundle.getBundle("i18n", Locale.forLanguageTag("en".equalsIgnoreCase(localeCode) ? "en" : "es"));
    return List.of(
        new AdminFilterOption("product_pulse", text(bundle, "campaigns_kind_product_pulse")),
        new AdminFilterOption("challenge_spotlight", text(bundle, "campaigns_kind_challenge_spotlight")),
        new AdminFilterOption("community_spotlight", text(bundle, "campaigns_kind_community_spotlight")),
        new AdminFilterOption("event_spotlight", text(bundle, "campaigns_kind_event_spotlight")));
  }

  private List<AdminFilterOption> channelOptions(String localeCode) {
    ResourceBundle bundle = ResourceBundle.getBundle("i18n", Locale.forLanguageTag("en".equalsIgnoreCase(localeCode) ? "en" : "es"));
    return List.of(
        new AdminFilterOption("discord", text(bundle, "campaigns_channel_discord")),
        new AdminFilterOption("bluesky", text(bundle, "campaigns_channel_bluesky")),
        new AdminFilterOption("mastodon", text(bundle, "campaigns_channel_mastodon")),
        new AdminFilterOption("linkedin", text(bundle, "campaigns_channel_linkedin")));
  }

  private Response redirectWithUpdate(
      String update,
      String draftId,
      AdminCampaignFilters filters,
      String extraKey,
      String extraValue) {
    UriBuilder builder =
        UriBuilder.fromPath("/private/admin/campaigns")
            .queryParam("updated", safe(update));
    if (!safe(draftId).isBlank()) {
      builder.queryParam("draft", safe(draftId));
    }
    appendFilters(builder, filters);
    if (extraKey != null && extraValue != null && !extraValue.isBlank()) {
      builder.queryParam(extraKey, safe(extraValue));
    }
    return Response.seeOther(builder.build()).build();
  }

  private Response redirectWithError(String error, String draftId, AdminCampaignFilters filters) {
    UriBuilder builder =
        UriBuilder.fromPath("/private/admin/campaigns")
            .queryParam("error", safe(error));
    if (!safe(draftId).isBlank()) {
      builder.queryParam("draft", safe(draftId));
    }
    appendFilters(builder, filters);
    return Response.seeOther(builder.build()).build();
  }

  private void appendFilters(UriBuilder builder, AdminCampaignFilters filters) {
    if (filters == null) {
      return;
    }
    if (!filters.query().isBlank()) {
      builder.queryParam("q", filters.query());
    }
    if (!filters.workflow().isBlank()) {
      builder.queryParam("workflow", filters.workflow());
    }
    if (!filters.kind().isBlank()) {
      builder.queryParam("kind", filters.kind());
    }
    if (!filters.channel().isBlank()) {
      builder.queryParam("channel", filters.channel());
    }
  }

  private static String safe(String raw) {
    return raw == null ? "" : raw.replaceAll("[^a-zA-Z0-9\\-_]", "");
  }

  public record AdminFilterOption(String value, String label) {}

  public record AdminCampaignFilters(
      String query, String workflow, String kind, String channel, int activeCount) {
    static AdminCampaignFilters sanitize(
        String query, String workflow, String kind, String channel) {
      String normalizedQuery = sanitizeQuery(query);
      String normalizedWorkflow =
          allow(workflow, java.util.Set.of("draft", "approved", "scheduled", "published"));
      String normalizedKind =
          allow(
              kind,
              java.util.Set.of(
                  "product_pulse",
                  "challenge_spotlight",
                  "community_spotlight",
                  "event_spotlight"));
      String normalizedChannel =
          allow(channel, java.util.Set.of("discord", "bluesky", "mastodon", "linkedin"));
      int active = 0;
      if (!normalizedQuery.isBlank()) {
        active++;
      }
      if (!normalizedWorkflow.isBlank()) {
        active++;
      }
      if (!normalizedKind.isBlank()) {
        active++;
      }
      if (!normalizedChannel.isBlank()) {
        active++;
      }
      return new AdminCampaignFilters(
          normalizedQuery, normalizedWorkflow, normalizedKind, normalizedChannel, active);
    }

    private static String sanitizeQuery(String raw) {
      if (raw == null) {
        return "";
      }
      String trimmed = raw.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
      return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    private static String allow(String raw, java.util.Set<String> allowed) {
      if (raw == null) {
        return "";
      }
      String normalized = raw.trim().toLowerCase(Locale.ROOT);
      return allowed.contains(normalized) ? normalized : "";
    }
  }

  public record AdminCampaignsCopy(
      String pageTitle,
      String subtitle,
      String heading,
      String intro,
      String backToPanel,
      String refresh,
      String refreshed,
      String filtersTitle,
      String filtersIntro,
      String filterQueryLabel,
      String filterQueryPlaceholder,
      String filterWorkflowLabel,
      String filterKindLabel,
      String filterChannelLabel,
      String filterAllLabel,
      String filterApplyLabel,
      String filterClearLabel,
      String filterResultsLabel,
      String filterEmptyLabel,
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
      String queueHealthTitle,
      String queueHealthIntro,
      String queueHealthStatusLabel,
      String queueHealthAttentionLabel,
      String queueHealthStaleDraftsLabel,
      String queueHealthStaleApprovedLabel,
      String queueHealthOverdueScheduledLabel,
      String queueHealthBlockedLabel,
      String queueHealthLinkedinLabel,
      String queueHealthEvaluatedLabel,
      String queueRisksTitle,
      String queueRisksIntro,
      String queueRisksEmptyLabel,
      String queueRiskAgeLabel,
      String queueRiskActionLabel,
      String auditTitle,
      String auditIntro,
      String auditEventLabel,
      String auditChannelLabel,
      String auditOutcomeLabel,
      String auditActorLabel,
      String auditEmptyLabel,
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
      String previewLandingLabel,
      String previewLengthLabel,
      String previewStatusLabel,
      String attributionTitle,
      String attributionIntro,
      String attributionTotalLabel,
      String attributionEmptyLabel,
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
