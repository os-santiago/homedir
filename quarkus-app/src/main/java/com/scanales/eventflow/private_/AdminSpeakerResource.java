package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.QueryParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.util.AdminUtils;

@Path("/private/admin/speakers")
public class AdminSpeakerResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance list(List<Speaker> speakers, String message);
    }

    @Inject
    SecurityIdentity identity;

    @Inject
    SpeakerService speakerService;

    private boolean isAdmin() {
        return AdminUtils.isAdmin(identity);
    }

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response list(@QueryParam("msg") String message) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        var speakers = speakerService.listSpeakers();
        return Response.ok(Templates.list(speakers, message)).build();
    }

    @POST
    @Authenticated
    public Response save(@FormParam("id") String id,
                         @FormParam("name") String name,
                         @FormParam("bio") String bio,
                         @FormParam("photoUrl") String photoUrl,
                         @FormParam("website") String website,
                         @FormParam("twitter") String twitter,
                         @FormParam("linkedin") String linkedin,
                         @FormParam("instagram") String instagram) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Speaker sp;
        if (id != null && !id.isBlank()) {
            sp = speakerService.getSpeaker(id);
            if (sp == null) {
                sp = new Speaker(id, name);
            }
        } else {
            id = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
            sp = new Speaker(id, name);
        }
        sp.setName(name);
        sp.setBio(bio);
        sp.setPhotoUrl(photoUrl);
        sp.setWebsite(website);
        sp.setTwitter(twitter);
        sp.setLinkedin(linkedin);
        sp.setInstagram(instagram);
        sp.setId(id);
        speakerService.saveSpeaker(sp);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/speakers")
                .build();
    }

    @POST
    @Path("{id}/delete")
    @Authenticated
    public Response delete(@PathParam("id") String id) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        speakerService.deleteSpeaker(id);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/speakers")
                .build();
    }
}
