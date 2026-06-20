package com.scanales.homedir.public_;

import com.scanales.homedir.service.TrendingService;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/")
@PermitAll
@Produces(MediaType.TEXT_HTML)
public class TrendingResource {

    private static final int[] COUNT_STEPS = {1, 3, 5, 10};

    @Inject
    TrendingService trendingService;

    @CheckedTemplate(basePath = "pages", requireTypeSafeExpressions = false)
    static class Templates {
        public static native TemplateInstance trending();
    }

    @GET
    @Path("/trending")
    public TemplateInstance trending(
            @QueryParam("period") @DefaultValue("daily") String period,
            @QueryParam("count") @DefaultValue("1") int count,
            @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
            @Context HttpHeaders headers) {

        List<TrendingService.TrendingProject> proyectos = trendingService.fetchTrending(
                null, count, period);

        // Compute next count for toggle button
        int nextCount = 1;
        for (int i = 0; i < COUNT_STEPS.length; i++) {
            if (COUNT_STEPS[i] == count) {
                nextCount = COUNT_STEPS[(i + 1) % COUNT_STEPS.length];
                break;
            }
        }
        // ponytail: label resolved client-side via i18n; server passes nextCount only
        boolean isMax = count == 10;

        return TemplateLocaleUtil.apply(Templates.trending(), localeCookie, headers)
                .data("proyectos", proyectos)
                .data("activePage", "trending")
                .data("period", period)
                .data("count", count)
                .data("nextCount", nextCount)
                .data("isMax", isMax);
    }
}
