package org.hyperagents.rdfsub.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Deploys an HTTP interface for the RDFSub Hub. Subscribers can use this interface to register
 * SPARQL queries and publishers can use this interface to update topic graphs. 
 * 
 * @author Andrei Ciortea, Interactions HSG
 *
 */
public class HttpAPIVerticle extends AbstractVerticle {
  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_PORT = 8090;
  
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpAPIVerticle.class.getName());
  
  @Override
  public void start() {
    int port = DEFAULT_PORT;
    String host = DEFAULT_HOST;
    
    JsonObject httpConfig = config().getJsonObject("http");
    
    if (httpConfig != null) {
      host = httpConfig.getString("host", DEFAULT_HOST);
      port = httpConfig.getInteger("port", DEFAULT_PORT);
    }
    
    vertx.createHttpServer()
      .requestHandler(createRouter())
      .listen(port, host);
    
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
    
    router.post("/topics").consumes("text/turtle").handler((routingContext) -> {
      String payload = routingContext.getBodyAsString();
      // TODO: validate subscribe payload syntax
      
      DeliveryOptions options = new DeliveryOptions().addHeader("method", "create-topic");
      vertx.eventBus().request("corese", payload, options, reply -> {
        if (reply.succeeded()) {
          routingContext.response().setStatusCode(200)
            .putHeader("Link", (String) reply.result().body())
            .end();
        } else {
          routingContext.response().setStatusCode(500).end();
        }
      });
    });
    
    router.get("/publish").handler((routingContext) -> {
      MultiMap params = routingContext.queryParams();
      
      if (params.size() == 5 && params.contains("action") && params.contains("topic") 
          && params.contains("subject") && params.contains("predicate") && params.contains("object")) {
        
        // TODO: validate params
        String action = params.get("action");
        String topic = params.get("topic");
        String subject = params.get("subject");
        String predicate = params.get("predicate");
        String object = params.get("object");
        
        String quad = "graph <" + topic + "> { <" + subject + "> <" + predicate + "> <" + object + "> . }";
        LOGGER.info("Data update: " + action + " " + quad);
        
        DeliveryOptions options = new DeliveryOptions().addHeader("method", action);
        vertx.eventBus().send("corese", quad, options);
        
        routingContext.response().setStatusCode(200).end();
      } else {
        routingContext.response().setStatusCode(400).end();
      }
    });
    
    // TODO: validate request
    router.post("/publish").consumes("application/sparql-update").handler((routingContext) -> {
      String payload = routingContext.getBodyAsString();
      
      DeliveryOptions options = new DeliveryOptions().addHeader("method", "update-query");
      vertx.eventBus().send("corese", payload, options);
      
      routingContext.response().setStatusCode(202).end();
    });
    
    return router;
  }
  
}
