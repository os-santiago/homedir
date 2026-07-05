package com.scanales.homedir.security;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Sets the Content-Security-Policy header on HTML responses using a per-request nonce. Non-HTML
 * responses (JSON, static assets served outside JAX-RS) skip CSP.
 *
 * @see CspService — the nonce is shared with Qute via {@code {app:cspNonce}}.
 */
@Provider
public class CspHeaderFilter implements ContainerResponseFilter {

  @Inject CspService cspService;

  // ponytail: kept as text not a structured builder; one header per request, see README before
  // adding more directives.
  @ConfigProperty(name = "homedir.csp.report-uri", defaultValue = "/csp-report")
  String reportUri;

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext res) {
    MediaType type = res.getMediaType();
    if (type == null || !type.isCompatible(MediaType.TEXT_HTML_TYPE)) {
      return;
    }
    String nonce = cspService.getNonce();
    String csp =
        "default-src 'self';"
            + " script-src 'self' 'nonce-"
            + nonce
            + "' https://cdn.tailwindcss.com;"
            + " style-src 'self' https://fonts.googleapis.com 'unsafe-inline';"
            + " font-src 'self' https://fonts.gstatic.com;"
            + " img-src 'self' data: https://cdn.simpleicons.org https://homedir.opensourcesantiago.io;"
            + " connect-src 'self';"
            + " object-src 'none';"
            + " base-uri 'self';"
            + " form-action 'self';"
            + " frame-ancestors 'none';"
            + " report-uri "
            + reportUri
            + ";";
    res.getHeaders().putSingle("Content-Security-Policy", csp);
  }
}
