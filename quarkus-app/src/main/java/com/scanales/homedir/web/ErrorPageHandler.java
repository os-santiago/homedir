package com.scanales.homedir.web;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ErrorPageHandler {

    @Inject
    Engine engine;

    void init(@Observes StartupEvent event, Router router) {
        router.errorHandler(404, ctx -> renderError(ctx, 404));
        router.errorHandler(403, ctx -> renderError(ctx, 403));
        router.errorHandler(500, ctx -> renderError(ctx, 500));
    }

    private void renderError(RoutingContext ctx, int statusCode) {
        if (ctx.response().ended()) {
            return;
        }
        String templateId = "errors/" + statusCode;
        Template template = engine.getTemplate(templateId);
        if (template == null) {
            ctx.response().setStatusCode(statusCode).end();
            return;
        }
        ctx.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "text/html; charset=utf-8")
            .end(template.render());
    }
}
