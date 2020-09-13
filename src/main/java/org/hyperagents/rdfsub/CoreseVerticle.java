package org.hyperagents.rdfsub;

import java.util.Optional;
import java.util.UUID;

import fr.inria.corese.core.Graph;
import fr.inria.corese.core.api.Loader;
import fr.inria.corese.core.load.Load;
import fr.inria.corese.core.load.LoadException;
import fr.inria.corese.core.print.ResultFormat;
import fr.inria.corese.core.query.QueryProcess;
import fr.inria.corese.kgram.core.Mappings;
import fr.inria.corese.sparql.exceptions.EngineException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * This verticle manages the hub's data using Corese.
 * 
 * @author Andrei Ciortea, Interactions HSG, University of St. Gallen
 *
 */
public class CoreseVerticle extends AbstractVerticle {
  private static final String NAMESPACE = "http://w3id.org/rdfsub/subscribers/";
  private static final Logger LOGGER = LoggerFactory.getLogger(CoreseVerticle.class.getName());

  private Graph graph;
  private Load loader;
  
  @Override
  public void start() {
    graph = Graph.create();
    loader = Load.create(graph);
    
    vertx.eventBus().consumer("corese", this::handleRequest);
  }
  
  private void handleRequest(Message<String> message) {
    String method = message.headers().get("method");
    
    switch (method) {
      case "subscribe":
        processSubscription(message.body());
        break;
      default:
        break;
    }

  }
  
  private void processSubscription(String subscription) {
    // TODO: Check the subscriber intent by validating the callback IRI.
    Future<Void> validCallbackFuture = Future.future(promise -> promise.complete());
    
    Optional<String> triggerIri = getObjectAsString(subscription, Loader.TURTLE_FORMAT, "us:trigger");
    LOGGER.info("Retrieving the trigger function: " + triggerIri);
    
    if (!triggerIri.isPresent()) {
      return;
    }
    
    // Retrieve async the linked function used for the trigger and check the syntax.
    Future<Void> validTriggerFuture = Future.future(promise -> {
      WebClient webClient = WebClient.create(vertx);
      webClient.getAbs(triggerIri.get())
        .as(BodyCodec.string())
        .send(ar -> {
          if (ar.succeeded()) {
            HttpResponse<String> response = ar.result();
            if (response.statusCode() == 200) {
              if (response.getHeader("Content-Type").equals("application/sparql-query")) {
                try {
                  LOGGER.info("Checking the trigger function's syntax:\n" + response.body());
                  
                  String query = "select (<" + triggerIri.get() + ">(xt:query(), xt:list(), xt:list()) "
                      + "as ?value) where {}";
                  
                  Mappings result = QueryProcess.create(graph).query(query + "\n" + response.body());
                  
                  //if (result.isError()) { // TODO: this will not work
                  if (result.getValue("?value") == null) {
                    LOGGER.info("The syntax of the trigger function is invalid.");
                    promise.fail("The syntax of the trigger function is invalid.");
                  } else {
                    promise.complete();
                  }
                } catch (EngineException e) {
                  LOGGER.info(e.getMessage());
                  promise.fail(e);
                }
              } else {
                promise.fail("Unsopported media type: " + response.getHeader("Content-Type"));
              }
            } else {
              promise.fail("Dereferencing the trigger function failed with status code: " 
                  + response.statusCode());
            }
          } else {
            LOGGER.info("Unable to retrieve the trigger function.");
            promise.fail("Unable to retrieve the trigger function.");
          }
        });
    });
    
    CompositeFuture.all(validCallbackFuture, validTriggerFuture).onComplete(ar -> {
      if (ar.succeeded()) {
        try {
          String subscriptionIRI = generateSubscriptionIRI();
          String registration = subscription.replaceAll("<>", "<" + subscriptionIRI + ">");
          
          loader.loadString(registration, Loader.TURTLE_FORMAT);
          LOGGER.info("Subscription saved successfully: " + subscriptionIRI);
        } catch (LoadException e) {
          e.printStackTrace();
        }
      }
    });
  }
  
  private Optional<String> getObjectAsString(String representation, int format, String prop) {
    try {
      Graph data = Graph.create();
      Load.create(data).loadString(representation, format);
      QueryProcess exec = QueryProcess.create(data);
      
      String query = "select ?object where { ?subject " + prop + " ?object }";
      String value = exec.query(query).getValue("?object").stringValue();
      
      return (value == null || value.isEmpty()) ? Optional.empty() : Optional.of(value);
      
    } catch (LoadException e) {
      LOGGER.debug(e.getMessage());
    } catch (EngineException e) {
      LOGGER.debug(e.getMessage());
    }
    
    return Optional.empty();
  }
  
  private String generateSubscriptionIRI() {
    String candidateIRI;
    
    do {
      candidateIRI = NAMESPACE.concat(UUID.randomUUID().toString());
    } while (subscriptionIriExists(candidateIRI));
    
    return candidateIRI;
  }
  
  private boolean subscriptionIriExists(String candidateIRI) {
    String query = "ask { <" + candidateIRI + "> a us:Subscriber }";
    
    try {
      Mappings result = QueryProcess.create(graph).query(query);
      
      // TODO: is there a more elegant way to check the result of an ASK?
      return ResultFormat.format(result).toString().contains("true") ? true : false;
    } catch (EngineException e) {
      LOGGER.info(e.getMessage());
    }
    
    return true;
  }
  
}
