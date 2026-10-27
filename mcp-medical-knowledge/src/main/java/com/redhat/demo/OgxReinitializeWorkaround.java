package com.redhat.demo;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Workaround for an OGX bug: when resolving an MCP connector_id, OGX sends "initialize"
 * a second time on an already-initialized session, which this server rejects
 * ("Unsupported method: initialize"). Dropping the stale session header turns the
 * repeated "initialize" into a fresh session. Remove once OGX is fixed.
 */
@ApplicationScoped
public class OgxReinitializeWorkaround {

    void register(@Observes Router router) {
        router.post("/mcp").order(-100).handler(BodyHandler.create()).handler(ctx -> {
            String body = ctx.body().asString();
            if (ctx.request().headers().contains("Mcp-Session-Id") && body != null && body.startsWith("{")
                    && "initialize".equals(new JsonObject(body).getString("method"))) {
                ctx.request().headers().remove("Mcp-Session-Id");
            }
            ctx.next();
        });
    }
}
