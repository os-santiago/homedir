package com.scanales.homedir.public_;

import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.Speaker;
import com.scanales.homedir.model.Talk;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.SpeakerPhotoProxyService;
import com.scanales.homedir.service.SpeakerService;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/speaker")
public class SpeakerResource {

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @GET
  @Path("/{id}/photo")
  @PermitAll
  public Response getSpeakerPhoto(
      @PathParam("id") String speakerId,
      @jakarta.ws.rs.HeaderParam("If-None-Match") String ifNoneMatch) {
    if (speakerId == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    SpeakerPhotoProxyService.PhotoResult result = photoProxyService.getPhoto(speakerId);

    if (result == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // Check ETag for 304 Not Modified
    if (ifNoneMatch != null && ifNoneMatch.equals(result.etag())) {
      return Response.notModified().tag(result.etag()).build();
    }

    return Response.ok(result.file().toFile())
        .type(result.contentType())
        .header("Cache-Control", "public, max-age=604800, immutable")
        .header("ETag", result.etag())
        .header("Vary", "Accept-Encoding")
        .build();
  }

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance detail(
        Speaker speaker, Map<String, List<Event>> talkEvents, Event themeEvent);
  }

  @Inject SpeakerService speakerService;

  @Inject EventService eventService;

  @Inject SpeakerPhotoProxyService photoProxyService;

  @GET
  @Path("{id}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance detail(
      @PathParam("id") String id,
      @jakarta.ws.rs.QueryParam("event") String eventId,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie) {
    Speaker sp = speakerService.getSpeaker(id);
    Map<String, List<Event>> talkEvents = new HashMap<>();
    if (sp != null && sp.getTalks() != null) {
      for (Talk t : sp.getTalks()) {
        List<Event> events = eventService.findEventsByTalk(t.getId());
        if (!events.isEmpty()) {
          talkEvents.put(t.getId(), events);
        }
      }
    }
    Event themeEvent = null;
    if (eventId != null && !eventId.isBlank()) {
      themeEvent = eventService.getEvent(eventId.trim());
    }
    return TemplateLocaleUtil.apply(Templates.detail(sp, talkEvents, themeEvent), localeCookie);
  }
}
