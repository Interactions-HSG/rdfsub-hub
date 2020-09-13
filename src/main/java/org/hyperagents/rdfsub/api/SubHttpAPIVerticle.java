package org.hyperagents.rdfsub.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * HTTP interface for handling subscriptions.
 * 
 * @author Andrei Ciortea, Interactions HSG, University of St. Gallen
 *
 */
public class SubHttpAPIVerticle extends AbstractVerticle {
  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_PORT = 8080;
  
  private static final Logger LOGGER = LoggerFactory.getLogger(SubHttpAPIVerticle.class.getName());
  
  @Override
  public void start() {
    vertx.createHttpServer()
      .requestHandler(createRouter())
      .listen(DEFAULT_PORT, DEFAULT_HOST);
    
    LOGGER.info("Subscriber HTTP API deployed");
  }
  
  private Router createRouter() {
    Router router = Router.router(vertx);
    
    router.route().handler(BodyHandler.create());
    
    router.get("/").handler((routingContext) -> {
      routingContext.response()
        .setStatusCode(200)
        .end("RDFSub Hub");
    });
    
    router.post("/subscription").consumes("text/turtle").handler((routingContext) -> {
      String payload = routingContext.getBodyAsString();
      // TODO: validate subscribe payload syntax
      
      DeliveryOptions options = new DeliveryOptions().addHeader("method", "subscribe");
      vertx.eventBus().send("corese", payload, options);
      
      routingContext.response().setStatusCode(202).end();
    });
    
    return router;
  }
  
}
