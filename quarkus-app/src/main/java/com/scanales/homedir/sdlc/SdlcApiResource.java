package com.scanales.homedir.sdlc;

import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Path("/api/sdlc")
@Authenticated
public class SdlcApiResource {
  private static final Map<String, Window> WINDOWS = new ConcurrentHashMap<>();
  @Inject SdlcObservabilityService service;
  @Inject SecurityIdentity identity;

  @GET @Path("status") public Response status() { return read(service.status()); }
  @GET @Path("heartbeat") public Response heartbeat() { return read(service.heartbeat()); }
  @GET @Path("pipeline") public Response pipeline() { return read(service.pipeline()); }
  @GET @Path("issues") public Response issues() { return read(service.issues()); }
  @GET @Path("prs") public Response prs() { return read(service.prs()); }
  @GET @Path("metrics") public Response metrics(@QueryParam("days") Integer days) { return read(service.metrics(days == null ? 30 : days)); }
  @GET @Path("anomalies") public Response anomalies() { return read(service.anomalies()); }
  @GET @Path("audit/{id}") public Response audit(@PathParam("id") String id) { if (id == null || !id.matches("[0-9]{1,10}")) return Response.status(400).entity(Map.of("error", "id must be a positive issue or PR number")).build(); return read(service.audit(id)); }
  @GET @Path("configuration") public Response configuration() { return read(service.configuration()); }

  @POST @Path("control/{action}")
  public Response control(@PathParam("action") String action) {
    if (!allowed(true)) return forbidden();
    if (action == null || !action.matches("pause|resume|reconcile|clear-locks")) return Response.status(400).entity(Map.of("error", "unsupported action")).build();
    try { return Response.ok(service.control(action, identity.getPrincipal().getName())).build(); }
    catch (IllegalArgumentException e) { return Response.status(400).entity(Map.of("error", e.getMessage())).build(); }
    catch (IOException e) { return Response.serverError().entity(Map.of("error", "worker state could not be updated")).build(); }
  }

  private Response read(Object entity) { if (!allowed(false)) return forbidden(); return Response.ok(entity).build(); }
  private boolean allowed(boolean manage) { if (manage ? !AdminUtils.canManageAdminBackoffice(identity) : !AdminUtils.canViewAdminBackoffice(identity)) return false; String key = identity.getPrincipal().getName(); Window w = WINDOWS.compute(key, (k, old) -> old == null || old.started.plusSeconds(60).isBefore(Instant.now()) ? new Window() : old); return w.requests.incrementAndGet() <= 120; }
  private Response forbidden() { return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "not authorized or rate limit exceeded")).build(); }
  private static final class Window { final Instant started = Instant.now(); final AtomicInteger requests = new AtomicInteger(); }
}
