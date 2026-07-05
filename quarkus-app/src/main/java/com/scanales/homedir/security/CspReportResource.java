package com.scanales.homedir.security;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * Receives Content-Security-Policy violation reports. Logs a redacted + truncated body so
 * query-string PII (session tokens in document-uri) never lands in logs verbatim.
 *
 * <p>ponytail: no rate-limit/storage yet; add when report volume causes log noise.
 */
@Path("/csp-report")
@PermitAll
public class CspReportResource {

  private static final Logger LOG = Logger.getLogger(CspReportResource.class);
  private static final int MAX_LOG = 512;

  @POST
  @Consumes({MediaType.APPLICATION_JSON, "application/csp-report", "application/reports+json"})
  public Response report(String body) {
    // Browsers POST a {"csp-report": {...}} envelope.
    if (body == null || body.isBlank()) {
      return Response.noContent().build();
    }
    String truncated =
        body.length() > MAX_LOG ? body.substring(0, MAX_LOG) + "...[truncated]" : body;
    String sanitized = sanitize(truncated);
    LOG.warnf("CSP violation report: %s", sanitized);
    return Response.noContent().build();
  }

  /** Visible for test. Strips every query string; CSP reports don't need params for debugging. */
  static String sanitize(String body) {
    return body.replaceAll("(?i)\\?[^\\s\"]+", "?[redacted]").replaceAll("[\\r\\n\\p{Cntrl}]", " ");
  }
}
