package com.scanales.homedir.private_;

import com.scanales.homedir.campaigns.CampaignService;
import com.scanales.homedir.campaigns.CampaignWorkflowState;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ResourceBundle;
import jakarta.ws.rs.core.UriBuilder;

/** Hidden admin surface for internal marketing campaign drafts. */
@Path("/private/admin/campaigns")
public class AdminCampaignsResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        AdminCampaignsCopy copy,
        String activePage,
        AdminCampaignFilters filters,
        List<AdminFilterOption> workflowOptions,
        List<AdminFilterOption> kindOptions,
        List<AdminFilterOption> channelOptions,
        CampaignService.CampaignPreviewSnapshot view,
        boolean refreshed,
        String updatedAction,
        String updatedDraft,
        String errorCode,
        String updatedCount);

    static native TemplateInstance detail(
        AdminCampaignsCopy copy,
        AdminCampaignFilters filters,
        CampaignService.CampaignDetailSnapshot detail,
        String draftId,
        String updatedAction,
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
      @QueryParam("error") String errorCode,
      @QueryParam("count") String updatedCount) {
    return renderIndexPage(
        "overview",
        headers,
        query,
        workflow,
        kind,
        channel,
        refreshed,
        updated,
        draftId,
        errorCode,
        updatedCount);
  }

  @GET
  @Path("content")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response content(
      @Context HttpHeaders headers,
      @QueryParam("q") String query,
      @QueryParam("workflow") String workflow,
      @QueryParam("kind") String kind,
      @QueryParam("channel") String channel,
      @QueryParam("refreshed") String refreshed,
      @QueryParam("updated") String updated,
      @QueryParam("draft") String draftId,
      @QueryParam("error") String errorCode,
      @QueryParam("count") String updatedCount) {
    return renderIndexPage(
        "content",
        headers,
        query,
        workflow,
        kind,
        channel,
        refreshed,
        updated,
        draftId,
        errorCode,
        updatedCount);
  }

  @GET
  @Path("channels")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response channels(
      @Context HttpHeaders headers,
      @QueryParam("q") String query,
      @QueryParam("workflow") String workflow,
      @QueryParam("kind") String kind,
      @QueryParam("channel") String channel,
      @QueryParam("refreshed") String refreshed,
      @QueryParam("updated") String updated,
      @QueryParam("draft") String draftId,
      @QueryParam("error") String errorCode,
      @QueryParam("count") String updatedCount) {
    return renderIndexPage(
        "channels",
        headers,
        query,
        workflow,
        kind,
        channel,
        refreshed,
        updated,
        draftId,
        errorCode,
        updatedCount);
  }

  @GET
  @Path("publish")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response publish(
      @Context HttpHeaders headers,
      @QueryParam("q") String query,
      @QueryParam("workflow") String workflow,
      @QueryParam("kind") String kind,
      @QueryParam("channel") String channel,
      @QueryParam("refreshed") String refreshed,
      @QueryParam("updated") String updated,
      @QueryParam("draft") String draftId,
      @QueryParam("error") String errorCode,
      @QueryParam("count") String updatedCount) {
    return renderIndexPage(
        "publish",
        headers,
        query,
        workflow,
        kind,
        channel,
        refreshed,
        updated,
        draftId,
        errorCode,
        updatedCount);
  }

  @GET
  @Path("monitor")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response monitor(
      @Context HttpHeaders headers,
      @QueryParam("q") String query,
      @QueryParam("workflow") String workflow,
      @QueryParam("kind") String kind,
      @QueryParam("channel") String channel,
      @QueryParam("refreshed") String refreshed,
      @QueryParam("updated") String updated,
      @QueryParam("draft") String draftId,
      @QueryParam("error") String errorCode,
      @QueryParam("count") String updatedCount) {
    return renderIndexPage(
        "monitor",
        headers,
        query,
        workflow,
        kind,
        channel,
        refreshed,
        updated,
        draftId,
        errorCode,
        updatedCount);
  }

  private Response renderIndexPage(
      String activePage,
      HttpHeaders headers,
      String query,
      String workflow,
      String kind,
      String channel,
      String refreshed,
      String updated,
      String draftId,
      String errorCode,
      String updatedCount) {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String localeCode = TemplateLocaleUtil.resolve(null, headers);
    AdminCampaignFilters filters = AdminCampaignFilters.sanitize(query, workflow, kind, channel);
    TemplateInstance template =
        Templates.index(
            localizedCopy(localeCode),
            normalizePage(activePage),
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
            safe(errorCode),
            safe(updatedCount));
    return Response.ok(TemplateLocaleUtil.apply(template, localeCode, headers)).build();
  }

  @GET
  @Path("{draftId}")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response detail(
      @PathParam("draftId") String draftId,
      @Context HttpHeaders headers,
      @QueryParam("q") String query,
      @QueryParam("workflow") String workflow,
      @QueryParam("kind") String kind,
      @QueryParam("channel") String channel,
      @QueryParam("updated") String updated,
      @QueryParam("error") String errorCode) {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String localeCode = TemplateLocaleUtil.resolve(null, headers);
    AdminCampaignFilters filters = AdminCampaignFilters.sanitize(query, workflow, kind, channel);
    CampaignService.CampaignDetailSnapshot detail =
        campaignService
            .detail(
                localeCode,
                draftId,
                new CampaignService.CampaignAdminFilters(
                    filters.query(), filters.workflow(), filters.kind(), filters.channel()))
            .orElse(null);
    if (detail == null) {
      return Response.seeOther(redirectWithErrorUri("not_found", draftId, filters, "content")).build();
    }
    TemplateInstance template =
        Templates.detail(
            localizedCopy(localeCode),
            filters,
            detail,
            safe(draftId),
            safe(updated),
            safe(errorCode));
    return Response.ok(TemplateLocaleUtil.apply(template, localeCode, headers)).build();
  }

  @POST
  @Path("bulk-action")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Authenticated
  public Response bulkAction(
      @FormParam("action") String action,
      @FormParam("draftIds") List<String> draftIds,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    AdminCampaignFilters filters = AdminCampaignFilters.sanitize(query, workflow, kind, channel);
    if (draftIds == null || draftIds.stream().map(AdminCampaignsResource::safe).allMatch(String::isBlank)) {
      return redirectWithError("bulk_no_selection", "", filters, normalizeReturnTo(returnTo, "content"));
    }
    String normalizedAction = safe(action);
    if (!Set.of("approve", "reset", "unschedule").contains(normalizedAction)) {
      return redirectWithError("invalid_bulk_action", "", filters, normalizeReturnTo(returnTo, "content"));
    }
    int changed = applyBulkAction(normalizedAction, draftIds, identity.getPrincipal().getName());
    return redirectWithUpdate(
        "bulk", "", filters, "count", String.valueOf(changed), normalizeReturnTo(returnTo, "content"));
  }

  @POST
  @Path("refresh")
  @Authenticated
  public Response refresh(
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo,
      @FormParam("currentDraft") String currentDraft) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.refreshDrafts();
    return redirectWithUpdate(
        "refresh",
        safe(currentDraft),
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        "refreshed",
        "1",
        normalizeReturnTo(returnTo, "content"));
  }

  @POST
  @Path("publish-now")
  @Authenticated
  public Response publishNow(
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo,
      @FormParam("currentDraft") String currentDraft) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.publishScheduledNow();
    return redirectWithUpdate(
        "publishscan",
        safe(currentDraft),
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "publish"));
  }

  @POST
  @Path("automation/refresh")
  @Authenticated
  public Response updateRefreshAutomation(
      @FormParam("enabled") String enabled,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.setRefreshAutomationEnabled(
        "true".equalsIgnoreCase(enabled), identity.getPrincipal().getName());
    return redirectWithUpdate(
        "refreshautomation",
        "refresh",
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "channels"));
  }

  @POST
  @Path("automation/publish")
  @Authenticated
  public Response updatePublishAutomation(
      @FormParam("enabled") String enabled,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.setPublishAutomationEnabled(
        "true".equalsIgnoreCase(enabled), identity.getPrincipal().getName());
    return redirectWithUpdate(
        "publishautomation",
        "publish",
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "channels"));
  }

  @POST
  @Path("automation/channel/{channelCode}")
  @Authenticated
  public Response updateChannelAutomation(
      @PathParam("channelCode") String channelCode,
      @FormParam("enabled") String enabled,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String normalizedChannel = normalizeAutomationChannel(channelCode);
    if (normalizedChannel.isBlank()) {
      return redirectWithError(
          "invalid_channel",
          channelCode,
          AdminCampaignFilters.sanitize(query, workflow, kind, channel),
          normalizeReturnTo(returnTo, "channels"));
    }
    campaignService.setChannelAutomationEnabled(
        normalizedChannel, "true".equalsIgnoreCase(enabled), identity.getPrincipal().getName());
    return redirectWithUpdate(
        "channelautomation",
        normalizedChannel,
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "channels"));
  }

  @POST
  @Path("rollout/channel/{channelCode}/ack")
  @Authenticated
  public Response updateChannelGoLiveAcknowledgement(
      @PathParam("channelCode") String channelCode,
      @FormParam("acknowledged") String acknowledged,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String normalizedChannel = normalizeAutomationChannel(channelCode);
    if (normalizedChannel.isBlank()) {
      return redirectWithError(
          "invalid_channel",
          channelCode,
          AdminCampaignFilters.sanitize(query, workflow, kind, channel),
          normalizeReturnTo(returnTo, "channels"));
    }
    campaignService.setChannelGoLiveAcknowledged(
        normalizedChannel,
        "true".equalsIgnoreCase(acknowledged),
        identity.getPrincipal().getName());
    return redirectWithUpdate(
        "rolloutack",
        normalizedChannel,
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "channels"));
  }

  @POST
  @Path("rollout/pilot/{channelCode}/select")
  @Authenticated
  public Response selectPilotLiveChannel(
      @PathParam("channelCode") String channelCode,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String normalizedChannel = normalizeAutomationChannel(channelCode);
    if (normalizedChannel.isBlank()) {
      return redirectWithError(
          "invalid_channel",
          channelCode,
          AdminCampaignFilters.sanitize(query, workflow, kind, channel),
          normalizeReturnTo(returnTo, "channels"));
    }
    campaignService.setPilotLiveChannel(normalizedChannel, identity.getPrincipal().getName());
    return redirectWithUpdate(
        "pilotselected",
        normalizedChannel,
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "channels"));
  }

  @POST
  @Path("rollout/pilot/clear")
  @Authenticated
  public Response clearPilotLiveChannel(
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.setPilotLiveChannel("", identity.getPrincipal().getName());
    return redirectWithUpdate(
        "pilotcleared",
        "pilot",
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "channels"));
  }

  @POST
  @Path("rollout/pilot/arm")
  @Authenticated
  public Response armPilotLiveChannel(
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.setPilotLiveArmed(true, identity.getPrincipal().getName());
    return redirectWithUpdate(
        "pilotarmed",
        "pilot",
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "channels"));
  }

  @POST
  @Path("rollout/pilot/disarm")
  @Authenticated
  public Response disarmPilotLiveChannel(
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.setPilotLiveArmed(false, identity.getPrincipal().getName());
    return redirectWithUpdate(
        "pilotdisarmed",
        "pilot",
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "channels"));
  }

  @POST
  @Path("rollout/pilot/verify")
  @Authenticated
  public Response updatePilotVerification(
      @FormParam("acknowledged") String acknowledged,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    boolean nextAcknowledged = "true".equalsIgnoreCase(acknowledged);
    campaignService.setPilotVerificationAcknowledged(nextAcknowledged, identity.getPrincipal().getName());
    return redirectWithUpdate(
        nextAcknowledged ? "pilotverified" : "pilotverificationcleared",
        "pilot",
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "publish"));
  }

  @POST
  @Path("rollout/pilot/decision")
  @Authenticated
  public Response updatePilotDecision(
      @FormParam("decision") String decision,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String normalizedDecision = decision == null ? "" : decision.trim().toLowerCase(Locale.ROOT);
    campaignService.setPilotDecision(normalizedDecision, identity.getPrincipal().getName());
    String updatedAction =
        switch (normalizedDecision) {
          case "approved" -> "pilotapproved";
          case "hold" -> "pilothold";
          default -> "pilotdecisioncleared";
        };
    return redirectWithUpdate(
        updatedAction,
        "pilot",
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "publish"));
  }

  @POST
  @Path("{draftId}/approve")
  @Authenticated
  public Response approve(
      @PathParam("draftId") String draftId,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.approveDraft(draftId, identity.getPrincipal().getName());
    return redirectWithUpdate(
        "approved",
        draftId,
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "content"));
  }

  @POST
  @Path("{draftId}/reset")
  @Authenticated
  public Response reset(
      @PathParam("draftId") String draftId,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.resetDraft(draftId);
    return redirectWithUpdate(
        "reset",
        draftId,
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "content"));
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
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    AdminCampaignFilters filters = AdminCampaignFilters.sanitize(query, workflow, kind, channel);
    if (scheduledFor == null || scheduledFor.isBlank()) {
      return redirectWithError("invalid_schedule", draftId, filters, normalizeReturnTo(returnTo, "publish"));
    }
    try {
      campaignService.scheduleDraft(draftId, LocalDateTime.parse(scheduledFor), identity.getPrincipal().getName());
      return redirectWithUpdate(
          "scheduled", draftId, filters, null, null, normalizeReturnTo(returnTo, "publish"));
    } catch (Exception e) {
      String errorCode =
          e instanceof IllegalStateException ? "not_ready" : "invalid_schedule";
      return redirectWithError(errorCode, draftId, filters, normalizeReturnTo(returnTo, "publish"));
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
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.unscheduleDraft(draftId);
    return redirectWithUpdate(
        "unscheduled",
        draftId,
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "publish"));
  }

  @POST
  @Path("{draftId}/mark-linkedin")
  @Authenticated
  public Response markLinkedin(
      @PathParam("draftId") String draftId,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    campaignService.markLinkedinPublished(draftId, identity.getPrincipal().getName());
    return redirectWithUpdate(
        "linkedin",
        draftId,
        AdminCampaignFilters.sanitize(query, workflow, kind, channel),
        null,
        null,
        normalizeReturnTo(returnTo, "publish"));
  }

  @POST
  @Path("{draftId}/retry-channel/{channelCode}")
  @Authenticated
  public Response retryChannel(
      @PathParam("draftId") String draftId,
      @PathParam("channelCode") String channelCode,
      @FormParam("q") String query,
      @FormParam("workflow") String workflow,
      @FormParam("kind") String kind,
      @FormParam("channel") String channel,
      @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    AdminCampaignFilters filters = AdminCampaignFilters.sanitize(query, workflow, kind, channel);
    String normalizedChannel = normalizeAutomationChannel(channelCode);
    if (normalizedChannel.isBlank() || "linkedin".equals(normalizedChannel)) {
      return redirectWithError("invalid_channel", draftId, filters, normalizeReturnTo(returnTo, "monitor"));
    }
    try {
      campaignService.retryChannel(draftId, normalizedChannel, identity.getPrincipal().getName());
      return redirectWithUpdate(
          "retried",
          draftId,
          filters,
          null,
          null,
          normalizeReturnTo(returnTo, "monitor"));
    } catch (IllegalArgumentException e) {
      return redirectWithError("invalid_channel", draftId, filters, normalizeReturnTo(returnTo, "monitor"));
    } catch (IllegalStateException e) {
      return redirectWithError("retry_not_ready", draftId, filters, normalizeReturnTo(returnTo, "monitor"));
    }
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
        text(bundle, "campaigns_admin_bulk_title"),
        text(bundle, "campaigns_admin_bulk_intro"),
        text(bundle, "campaigns_admin_bulk_select_label"),
        text(bundle, "campaigns_admin_bulk_action_label"),
        text(bundle, "campaigns_admin_bulk_apply"),
        text(bundle, "campaigns_admin_bulk_action_approve"),
        text(bundle, "campaigns_admin_bulk_action_reset"),
        text(bundle, "campaigns_admin_bulk_action_unschedule"),
        text(bundle, "campaigns_admin_updated_bulk"),
        text(bundle, "campaigns_admin_filter_results"),
        text(bundle, "campaigns_admin_filter_empty"),
        text(bundle, "campaigns_admin_detail_back"),
        text(bundle, "campaigns_admin_detail_overview"),
        text(bundle, "campaigns_admin_detail_preview"),
        text(bundle, "campaigns_admin_detail_attribution"),
        text(bundle, "campaigns_admin_detail_audit"),
        text(bundle, "campaigns_admin_detail_risks"),
        text(bundle, "campaigns_admin_detail_linkedin"),
        text(bundle, "campaigns_admin_detail_empty_audit"),
        text(bundle, "campaigns_admin_detail_empty_risks"),
        text(bundle, "campaigns_admin_detail_not_found"),
        text(bundle, "campaigns_admin_updated_approved"),
        text(bundle, "campaigns_admin_updated_reset"),
        text(bundle, "campaigns_admin_updated_scheduled"),
        text(bundle, "campaigns_admin_updated_unscheduled"),
        text(bundle, "campaigns_admin_updated_publishscan"),
        text(bundle, "campaigns_admin_updated_linkedin"),
        text(bundle, "campaigns_admin_updated_retry"),
        text(bundle, "campaigns_admin_updated_refresh_automation"),
        text(bundle, "campaigns_admin_updated_publish_automation"),
        text(bundle, "campaigns_admin_updated_channel_automation"),
        text(bundle, "campaigns_admin_updated_rollout_ack"),
        text(bundle, "campaigns_admin_updated_pilot_selected"),
        text(bundle, "campaigns_admin_updated_pilot_cleared"),
        text(bundle, "campaigns_admin_updated_pilot_armed"),
        text(bundle, "campaigns_admin_updated_pilot_disarmed"),
        text(bundle, "campaigns_admin_updated_pilot_verified"),
        text(bundle, "campaigns_admin_updated_pilot_verification_cleared"),
        text(bundle, "campaigns_admin_error_invalid_schedule"),
        text(bundle, "campaigns_admin_error_not_ready"),
        text(bundle, "campaigns_admin_error_retry_not_ready"),
        text(bundle, "campaigns_admin_error_bulk_no_selection"),
        text(bundle, "campaigns_admin_error_invalid_bulk_action"),
        text(bundle, "campaigns_admin_error_invalid_channel"),
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
        text(bundle, "campaigns_admin_business_title"),
        text(bundle, "campaigns_admin_business_intro"),
        text(bundle, "campaigns_admin_business_total_visits"),
        text(bundle, "campaigns_admin_business_drafts_with_traffic"),
        text(bundle, "campaigns_admin_business_average_visits"),
        text(bundle, "campaigns_admin_business_best_channel"),
        text(bundle, "campaigns_admin_business_top_draft"),
        text(bundle, "campaigns_admin_business_highlights"),
        text(bundle, "campaigns_admin_business_visits"),
        text(bundle, "campaigns_admin_business_dominant_channel"),
        text(bundle, "campaigns_admin_business_none"),
        text(bundle, "campaigns_admin_rollout_title"),
        text(bundle, "campaigns_admin_rollout_intro"),
        text(bundle, "campaigns_admin_rollout_status_label"),
        text(bundle, "campaigns_admin_rollout_ready_label"),
        text(bundle, "campaigns_admin_rollout_blocked_label"),
        text(bundle, "campaigns_admin_rollout_dry_run_label"),
        text(bundle, "campaigns_admin_rollout_acknowledged_label"),
        text(bundle, "campaigns_admin_rollout_pilot_label"),
        text(bundle, "campaigns_admin_rollout_pilot_updated_at_label"),
        text(bundle, "campaigns_admin_rollout_pilot_updated_by_label"),
        text(bundle, "campaigns_admin_rollout_activation_label"),
        text(bundle, "campaigns_admin_rollout_activation_updated_at_label"),
        text(bundle, "campaigns_admin_rollout_activation_updated_by_label"),
        text(bundle, "campaigns_admin_rollout_activation_armed"),
        text(bundle, "campaigns_admin_rollout_activation_disarmed"),
        text(bundle, "campaigns_admin_rollout_evaluated_label"),
        text(bundle, "campaigns_admin_rollout_recommendation_label"),
        text(bundle, "campaigns_admin_rollout_ack_status_label"),
        text(bundle, "campaigns_admin_rollout_ack_by_label"),
        text(bundle, "campaigns_admin_rollout_ack_at_label"),
        text(bundle, "campaigns_admin_rollout_ack_action"),
        text(bundle, "campaigns_admin_rollout_clear_ack_action"),
        text(bundle, "campaigns_admin_rollout_select_pilot_action"),
        text(bundle, "campaigns_admin_rollout_clear_pilot_action"),
        text(bundle, "campaigns_admin_rollout_arm_pilot_action"),
        text(bundle, "campaigns_admin_rollout_disarm_pilot_action"),
        text(bundle, "campaigns_admin_runbook_title"),
        text(bundle, "campaigns_admin_runbook_intro"),
        text(bundle, "campaigns_admin_runbook_status_label"),
        text(bundle, "campaigns_admin_runbook_completed_label"),
        text(bundle, "campaigns_admin_runbook_pending_label"),
        text(bundle, "campaigns_admin_runbook_target_label"),
        text(bundle, "campaigns_admin_runbook_recommendation_label"),
        text(bundle, "campaigns_admin_runbook_step_status_label"),
        text(bundle, "campaigns_admin_pilot_verification_title"),
        text(bundle, "campaigns_admin_pilot_verification_intro"),
        text(bundle, "campaigns_admin_pilot_verification_status_label"),
        text(bundle, "campaigns_admin_pilot_verification_target_label"),
        text(bundle, "campaigns_admin_pilot_verification_state_label"),
        text(bundle, "campaigns_admin_pilot_verification_published_count_label"),
        text(bundle, "campaigns_admin_pilot_verification_last_published_at_label"),
        text(bundle, "campaigns_admin_pilot_verification_last_draft_label"),
        text(bundle, "campaigns_admin_pilot_verification_acknowledged_at_label"),
        text(bundle, "campaigns_admin_pilot_verification_acknowledged_by_label"),
        text(bundle, "campaigns_admin_pilot_verification_recommendation_label"),
        text(bundle, "campaigns_admin_pilot_verification_action_label"),
        text(bundle, "campaigns_admin_pilot_verification_mark_action"),
        text(bundle, "campaigns_admin_pilot_verification_clear_action"),
        new PilotDecisionCopy(
            text(bundle, "campaigns_admin_updated_pilot_approved"),
            text(bundle, "campaigns_admin_updated_pilot_hold"),
            text(bundle, "campaigns_admin_updated_pilot_decision_cleared"),
            text(bundle, "campaigns_admin_pilot_decision_title"),
            text(bundle, "campaigns_admin_pilot_decision_intro"),
            text(bundle, "campaigns_admin_pilot_decision_status_label"),
            text(bundle, "campaigns_admin_pilot_decision_target_label"),
            text(bundle, "campaigns_admin_pilot_decision_verification_label"),
            text(bundle, "campaigns_admin_pilot_decision_state_label"),
            text(bundle, "campaigns_admin_pilot_decision_decided_at_label"),
            text(bundle, "campaigns_admin_pilot_decision_decided_by_label"),
            text(bundle, "campaigns_admin_pilot_decision_recommendation_label"),
            text(bundle, "campaigns_admin_pilot_decision_action_label"),
            text(bundle, "campaigns_admin_pilot_decision_approve_action"),
            text(bundle, "campaigns_admin_pilot_decision_hold_action"),
            text(bundle, "campaigns_admin_pilot_decision_clear_action")),
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
        text(bundle, "campaigns_admin_recovery_title"),
        text(bundle, "campaigns_admin_recovery_intro"),
        text(bundle, "campaigns_admin_recovery_actionable"),
        text(bundle, "campaigns_admin_recovery_retryable"),
        text(bundle, "campaigns_admin_recovery_blocked"),
        text(bundle, "campaigns_admin_recovery_manual"),
        text(bundle, "campaigns_admin_recovery_evaluated"),
        text(bundle, "campaigns_admin_recovery_empty"),
        text(bundle, "campaigns_admin_recovery_channel"),
        text(bundle, "campaigns_admin_recovery_outcome"),
        text(bundle, "campaigns_admin_recovery_recommendation"),
        text(bundle, "campaigns_admin_recovery_action"),
        text(bundle, "campaigns_admin_recovery_age"),
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
        text(bundle, "campaigns_admin_schedule_readiness"),
        text(bundle, "campaigns_admin_schedule_for"),
        text(bundle, "campaigns_admin_best_window"),
        text(bundle, "campaigns_admin_published_channels"),
        text(bundle, "campaigns_admin_publisher_status"),
        text(bundle, "campaigns_admin_publisher_intro"),
        text(bundle, "campaigns_admin_publisher_global"),
        text(bundle, "campaigns_admin_publisher_refresh_automation"),
        text(bundle, "campaigns_admin_publisher_publish_automation"),
        text(bundle, "campaigns_admin_publisher_runtime_channel"),
        text(bundle, "campaigns_admin_publisher_effective"),
        text(bundle, "campaigns_admin_publisher_updated"),
        text(bundle, "campaigns_admin_publisher_updated_by"),
        text(bundle, "campaigns_admin_publisher_dry_run"),
        text(bundle, "campaigns_admin_publisher_channel"),
        text(bundle, "campaigns_admin_publisher_webhook"),
        text(bundle, "campaigns_admin_publisher_rate_limit"),
        text(bundle, "campaigns_admin_publisher_run"),
        text(bundle, "campaigns_admin_btn_pause_refresh"),
        text(bundle, "campaigns_admin_btn_resume_refresh"),
        text(bundle, "campaigns_admin_btn_pause_publish"),
        text(bundle, "campaigns_admin_btn_resume_publish"),
        text(bundle, "campaigns_admin_btn_disable_channel_automation"),
        text(bundle, "campaigns_admin_btn_enable_channel_automation"),
        text(bundle, "campaigns_admin_btn_approve"),
        text(bundle, "campaigns_admin_btn_reset"),
        text(bundle, "campaigns_admin_btn_schedule"),
        text(bundle, "campaigns_admin_btn_unschedule"),
        text(bundle, "campaigns_admin_btn_mark_linkedin"),
        text(bundle, "campaigns_admin_btn_retry_channel"));
  }

  private static String text(ResourceBundle bundle, String key) {
    return bundle.containsKey(key) ? bundle.getString(key) : key;
  }

  private int applyBulkAction(String action, List<String> draftIds, String actor) {
    Set<String> uniqueIds = new LinkedHashSet<>();
    for (String draftId : draftIds) {
      String safeDraftId = safe(draftId);
      if (!safeDraftId.isBlank()) {
        uniqueIds.add(safeDraftId);
      }
    }
    int changed = 0;
    for (String draftId : uniqueIds) {
      CampaignService.CampaignPreviewCard draft =
          campaignService.preview("es").drafts().stream()
              .filter(item -> item.id().equals(draftId))
              .findFirst()
              .orElse(null);
      if (draft == null) {
        continue;
      }
      CampaignWorkflowState workflow = CampaignWorkflowState.valueOf(draft.workflowStateCode().toUpperCase(Locale.ROOT));
      switch (action) {
        case "approve" -> {
          if (workflow == CampaignWorkflowState.DRAFT) {
            campaignService.approveDraft(draftId, actor);
            changed++;
          }
        }
        case "reset" -> {
          if (workflow == CampaignWorkflowState.APPROVED || workflow == CampaignWorkflowState.SCHEDULED) {
            campaignService.resetDraft(draftId);
            changed++;
          }
        }
        case "unschedule" -> {
          if (workflow == CampaignWorkflowState.SCHEDULED) {
            campaignService.unscheduleDraft(draftId);
            changed++;
          }
        }
        default -> {
        }
      }
    }
    return changed;
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
      String extraValue,
      String returnTo) {
    UriBuilder builder = redirectUri(returnTo, draftId);
    builder.queryParam("updated", safe(update));
    if (!safe(draftId).isBlank()) {
      builder.queryParam("draft", safe(draftId));
    }
    appendFilters(builder, filters);
    if (extraKey != null && extraValue != null && !extraValue.isBlank()) {
      builder.queryParam(extraKey, safe(extraValue));
    }
    return Response.seeOther(builder.build()).build();
  }

  private Response redirectWithError(
      String error, String draftId, AdminCampaignFilters filters, String returnTo) {
    return Response.seeOther(redirectWithErrorUri(error, draftId, filters, returnTo)).build();
  }

  private URI redirectWithErrorUri(String error, String draftId, AdminCampaignFilters filters) {
    return redirectWithErrorUri(error, draftId, filters, "overview");
  }

  private URI redirectWithErrorUri(
      String error, String draftId, AdminCampaignFilters filters, String returnTo) {
    UriBuilder builder = redirectUri(returnTo, draftId);
    if (!safe(draftId).isBlank()) {
      builder.queryParam("draft", safe(draftId));
    }
    builder.queryParam("error", safe(error));
    appendFilters(builder, filters);
    return builder.build();
  }

  private UriBuilder redirectUri(String returnTo, String draftId) {
    return switch (normalizePage(returnTo)) {
      case "detail" ->
          UriBuilder.fromPath("/private/admin/campaigns/{draftId}")
              .resolveTemplate("draftId", safe(draftId));
      case "content" -> UriBuilder.fromPath("/private/admin/campaigns/content");
      case "channels" -> UriBuilder.fromPath("/private/admin/campaigns/channels");
      case "publish" -> UriBuilder.fromPath("/private/admin/campaigns/publish");
      case "monitor" -> UriBuilder.fromPath("/private/admin/campaigns/monitor");
      default -> UriBuilder.fromPath("/private/admin/campaigns");
    };
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

  private static String normalizeAutomationChannel(String raw) {
    if (raw == null) {
      return "";
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "discord", "bluesky", "mastodon" -> raw.trim().toLowerCase(Locale.ROOT);
      default -> "";
    };
  }

  private static String normalizeReturnTo(String raw, String fallback) {
    return raw == null || raw.isBlank() ? normalizePage(fallback) : normalizePage(raw);
  }

  private static String normalizePage(String raw) {
    if (raw == null) {
      return "overview";
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "overview", "content", "channels", "publish", "monitor", "detail" ->
          raw.trim().toLowerCase(Locale.ROOT);
      default -> "overview";
    };
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

    public String querySuffix() {
      StringBuilder builder = new StringBuilder();
      appendParam(builder, "q", query);
      appendParam(builder, "workflow", workflow);
      appendParam(builder, "kind", kind);
      appendParam(builder, "channel", channel);
      return builder.toString();
    }

    private static void appendParam(StringBuilder builder, String key, String value) {
      if (value == null || value.isBlank()) {
        return;
      }
      builder.append(builder.isEmpty() ? "?" : "&")
          .append(key)
          .append("=")
          .append(encode(value));
    }

    private static String encode(String value) {
      return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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
      String bulkTitle,
      String bulkIntro,
      String bulkSelectLabel,
      String bulkActionLabel,
      String bulkApplyLabel,
      String bulkApproveOption,
      String bulkResetOption,
      String bulkUnscheduleOption,
      String updatedBulk,
      String filterResultsLabel,
      String filterEmptyLabel,
      String detailBackLabel,
      String detailOverviewTitle,
      String detailPreviewTitle,
      String detailAttributionTitle,
      String detailAuditTitle,
      String detailRisksTitle,
      String detailLinkedinTitle,
      String detailEmptyAuditLabel,
      String detailEmptyRisksLabel,
      String detailNotFoundLabel,
      String updatedApproved,
      String updatedReset,
      String updatedScheduled,
      String updatedUnscheduled,
      String updatedPublishScan,
      String updatedLinkedin,
      String updatedRetry,
      String updatedRefreshAutomation,
      String updatedPublishAutomation,
      String updatedChannelAutomation,
      String updatedRolloutAck,
      String updatedPilotSelected,
      String updatedPilotCleared,
      String updatedPilotArmed,
      String updatedPilotDisarmed,
      String updatedPilotVerified,
      String updatedPilotVerificationCleared,
      String invalidSchedule,
      String notReady,
      String retryNotReady,
      String bulkNoSelection,
      String invalidBulkAction,
      String invalidChannel,
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
      String businessTitle,
      String businessIntro,
      String businessTotalVisitsLabel,
      String businessDraftsWithTrafficLabel,
      String businessAverageVisitsLabel,
      String businessBestChannelLabel,
      String businessTopDraftLabel,
      String businessHighlightsLabel,
      String businessVisitsLabel,
      String businessDominantChannelLabel,
      String businessNoneLabel,
      String rolloutTitle,
      String rolloutIntro,
      String rolloutStatusLabel,
      String rolloutReadyLabel,
      String rolloutBlockedLabel,
      String rolloutDryRunLabel,
      String rolloutAcknowledgedLabel,
      String rolloutPilotLabel,
      String rolloutPilotUpdatedAtLabel,
      String rolloutPilotUpdatedByLabel,
      String rolloutActivationLabel,
      String rolloutActivationUpdatedAtLabel,
      String rolloutActivationUpdatedByLabel,
      String rolloutActivationArmedLabel,
      String rolloutActivationDisarmedLabel,
      String rolloutEvaluatedLabel,
      String rolloutRecommendationLabel,
      String rolloutAckStatusLabel,
      String rolloutAckByLabel,
      String rolloutAckAtLabel,
      String rolloutAckActionLabel,
      String rolloutClearAckActionLabel,
      String rolloutSelectPilotActionLabel,
      String rolloutClearPilotActionLabel,
      String rolloutArmPilotActionLabel,
      String rolloutDisarmPilotActionLabel,
      String runbookTitle,
      String runbookIntro,
      String runbookStatusLabel,
      String runbookCompletedLabel,
      String runbookPendingLabel,
      String runbookTargetLabel,
      String runbookRecommendationLabel,
      String runbookStepStatusLabel,
      String pilotVerificationTitle,
      String pilotVerificationIntro,
      String pilotVerificationStatusLabel,
      String pilotVerificationTargetLabel,
      String pilotVerificationStateLabel,
      String pilotVerificationPublishedCountLabel,
      String pilotVerificationLastPublishedAtLabel,
      String pilotVerificationLastDraftLabel,
      String pilotVerificationAcknowledgedAtLabel,
      String pilotVerificationAcknowledgedByLabel,
      String pilotVerificationRecommendationLabel,
      String pilotVerificationActionLabel,
      String pilotVerificationMarkActionLabel,
      String pilotVerificationClearActionLabel,
      PilotDecisionCopy pilotDecision,
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
      String recoveryTitle,
      String recoveryIntro,
      String recoveryActionableLabel,
      String recoveryRetryableLabel,
      String recoveryBlockedLabel,
      String recoveryManualLabel,
      String recoveryEvaluatedLabel,
      String recoveryEmptyLabel,
      String recoveryChannelLabel,
      String recoveryOutcomeLabel,
      String recoveryRecommendationLabel,
      String recoveryActionLabel,
      String recoveryAgeLabel,
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
      String scheduleReadinessLabel,
      String scheduleForLabel,
      String bestWindowLabel,
      String publishedChannelsLabel,
      String publisherStatusLabel,
      String publisherIntro,
      String publisherGlobalLabel,
      String publisherRefreshAutomationLabel,
      String publisherPublishAutomationLabel,
      String publisherRuntimeChannelLabel,
      String publisherEffectiveLabel,
      String publisherUpdatedLabel,
      String publisherUpdatedByLabel,
      String publisherDryRunLabel,
      String publisherChannelLabel,
      String publisherConfiguredLabel,
      String publisherRateLimitLabel,
      String publisherRunLabel,
      String pauseRefreshLabel,
      String resumeRefreshLabel,
      String pausePublishLabel,
      String resumePublishLabel,
      String disableChannelAutomationLabel,
      String enableChannelAutomationLabel,
      String approveLabel,
      String resetLabel,
      String scheduleLabel,
      String unscheduleLabel,
      String markLinkedinLabel,
      String retryChannelLabel) {}

  public record PilotDecisionCopy(
      String updatedApproved,
      String updatedHold,
      String updatedCleared,
      String title,
      String intro,
      String statusLabel,
      String targetLabel,
      String verificationLabel,
      String stateLabel,
      String decidedAtLabel,
      String decidedByLabel,
      String recommendationLabel,
      String actionLabel,
      String approveActionLabel,
      String holdActionLabel,
      String clearActionLabel) {}
}
