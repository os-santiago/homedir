package com.scanales.homedir.public_;

import com.scanales.homedir.service.TrendingService;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/")
@PermitAll
@Produces(MediaType.TEXT_HTML)
public class TrendingResource {

    @Inject
    TrendingService trendingService;

    // ponytail: 10 trending repos on the page
    private static final int TRENDING_COUNT = 10;

    @CheckedTemplate(basePath = "pages", requireTypeSafeExpressions = false)
    static class Templates {
        public static native TemplateInstance trending();
    }

    @GET
    @Path("/trending")
    public TemplateInstance trending(
            @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
            @Context HttpHeaders headers) {

        List<TrendingService.TrendingProject> proyectos = trendingService.fetchTrending(
                null, TRENDING_COUNT, "daily");

        return TemplateLocaleUtil.apply(Templates.trending(), localeCookie, headers)
                .data("proyectos", proyectos)
                .data("activePage", "trending");
    }
}
