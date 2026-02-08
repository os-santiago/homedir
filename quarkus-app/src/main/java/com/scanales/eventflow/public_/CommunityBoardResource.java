package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.CommunityBoardService;
import com.scanales.eventflow.service.UserSessionService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/community")
@PermitAll
public class CommunityBoardResource {

  @Inject CommunityBoardService boardService;
  @Inject SecurityIdentity identity;
  @Inject UserSessionService userSessionService;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance board(CommunityBoardService.BoardSummary summary);

    static native TemplateInstance board_group(
        CommunityBoardService.BoardGroupPage page, CommunityBoardService.GroupMeta meta);

    static native TemplateInstance member(
        CommunityBoardService.BoardMember member, CommunityBoardService.GroupMeta groupMeta);
  }

  @GET
  @Path("/feed")
  public Response feed() {
    return Response.seeOther(URI.create("/comunidad?view=new")).build();
  }

  @GET
  @Path("/picks")
  public Response picks() {
    return Response.seeOther(URI.create("/comunidad?view=featured")).build();
  }

  @GET
  @Path("/board")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance board() {
    return withLayoutData(Templates.board(boardService.summary()));
  }

  @GET
  @Path("/board/{group}")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance boardGroup(
      @PathParam("group") String group,
      @QueryParam("q") String query,
      @QueryParam("limit") Integer limit,
      @QueryParam("offset") Integer offset) {
    CommunityBoardService.BoardGroupPage page =
        boardService
            .groupPage(group, query, limit, offset)
            .orElseThrow(NotFoundException::new);
    CommunityBoardService.GroupMeta meta =
        boardService.groupMeta(group).orElseThrow(NotFoundException::new);
    return withLayoutData(Templates.board_group(page, meta));
  }

  @GET
  @Path("/member/{ref}")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance member(@PathParam("ref") String ref) {
    CommunityBoardService.BoardMember member =
        boardService.findByRef(ref).orElseThrow(NotFoundException::new);
    CommunityBoardService.GroupMeta groupMeta =
        boardService.groupMeta(member.groupKey()).orElse(new CommunityBoardService.GroupMeta("unknown", "Member", ""));
    return withLayoutData(Templates.member(member, groupMeta));
  }

  private TemplateInstance withLayoutData(TemplateInstance templateInstance) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated && identity.getPrincipal() != null ? identity.getPrincipal().getName() : "";
    return templateInstance
        .data("activePage", "comunidad")
        .data("userAuthenticated", authenticated)
        .data("userName", userName)
        .data("userSession", userSessionService.getCurrentSession())
        .data("userInitial", initialFrom(userName));
  }

  private static String initialFrom(String name) {
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

