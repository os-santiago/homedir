package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@PermitAll
@Produces(MediaType.TEXT_HTML)
public class MarketingPagesResource {

  @Inject UsageMetricsService metrics;

  @ConfigProperty(name = "homedir.ui.v2.enabled", defaultValue = "true")
  boolean uiV2Enabled;

  @CheckedTemplate(basePath = "pages")
  static class Templates {
    public static native TemplateInstance docs();

    public static native TemplateInstance contacto();

    public static native TemplateInstance privacy();

    public static native TemplateInstance terms();
  }

  @GET
  @Path("/docs")
  public TemplateInstance docs(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @Context HttpHeaders headers, @Context RoutingContext context) {
    metrics.recordPageView("/docs", headers, context);
    if (uiV2Enabled) {
      return TemplateLocaleUtil.apply(Templates.docs(), localeCookie);
    }
    // TODO: definir template de fallback si en el futuro se desea una versión mínima
    return TemplateLocaleUtil.apply(Templates.docs(), localeCookie);
  }

  @GET
  @Path("/contacto")
  public TemplateInstance contacto(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @Context HttpHeaders headers, @Context RoutingContext context) {
    metrics.recordPageView("/contacto", headers, context);
    if (uiV2Enabled) {
      return TemplateLocaleUtil.apply(Templates.contacto(), localeCookie);
    }
    // TODO: definir template de fallback si en el futuro se desea una versión mínima
    return TemplateLocaleUtil.apply(Templates.contacto(), localeCookie);
  }

  @GET
  @Path("/privacy-policy")
  public TemplateInstance privacyPolicy(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @Context HttpHeaders headers,
      @Context RoutingContext context) {
    metrics.recordPageView("/privacy-policy", headers, context);
    return TemplateLocaleUtil.apply(Templates.privacy(), localeCookie);
  }

  @GET
  @Path("/politica-de-privacidad")
  public TemplateInstance privacyPolicyEs(
      @Context HttpHeaders headers,
      @Context RoutingContext context) {
    metrics.recordPageView("/politica-de-privacidad", headers, context);
    return TemplateLocaleUtil.apply(Templates.privacy(), "es");
  }

  @GET
  @Path("/terms-of-service")
  public TemplateInstance termsOfService(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @Context HttpHeaders headers,
      @Context RoutingContext context) {
    metrics.recordPageView("/terms-of-service", headers, context);
    return TemplateLocaleUtil.apply(Templates.terms(), localeCookie);
  }

  @GET
  @Path("/condiciones-del-servicio")
  public TemplateInstance termsOfServiceEs(
      @Context HttpHeaders headers,
      @Context RoutingContext context) {
    metrics.recordPageView("/condiciones-del-servicio", headers, context);
    return TemplateLocaleUtil.apply(Templates.terms(), "es");
  }
}
