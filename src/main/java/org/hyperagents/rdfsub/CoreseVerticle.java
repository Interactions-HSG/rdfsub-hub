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
 * Manages the hub's data using Corese. Each publisher can publish data under a topic IRI (and all
 * triples are stored in a graph whose name is the topic IRI). Information on subscribers is 
 * maintained in a separate named graph.
 * 
 * @author Andrei Ciortea, Interactions HSG, University of St. Gallen
 *
 */
public class CoreseVerticle extends AbstractVerticle {
  private static final String SUBSCRIBER_GRAPH_IRI = "http://w3id.org/rdfsub/subscribers/";
  
  private static final Logger LOGGER = LoggerFactory.getLogger(CoreseVerticle.class.getName());
  
  private Graph graph;
  
  @Override
  public void start() {
    graph = Graph.create();
    
    vertx.eventBus().consumer("corese", this::handleRequest);
  }
  
  private void handleRequest(Message<String> message) {
    String method = message.headers().get("method");
    
    switch (method) {
      case "subscribe":
        processSubscription(message.body());
        break;
      case "insert":
        updateTriple("insert data", message.body());
        LOGGER.info("Triple inserted: " + message.body());
        break;
      case "delete":
        updateTriple("delete data", message.body());
        LOGGER.info("Triple deleted: " + message.body());
        break;
      default:
        break;
    }

  }
  
  private void updateTriple(String updateMethod, String quad) {
    // TODO: load the function from a resource file
    String processFunction = "@update\n" + 
        "function us:processRegisteredQueries(q, del, ins) {\n" + 
        "  for (select ?callback ?query ?trigger from <" + SUBSCRIBER_GRAPH_IRI + "> "
            + "where { ?x a us:Subscriber ; us:callback ?callback ; us:query ?query ; us:trigger ?trigger . }) {\n" + 
        "    xt:print(?trigger);\n" +
        "    if (funcall (?trigger, q, del, ins)) {\n" + 
        "      xt:print(\"Triggered, performing query...\");\n" +
        "      dispatcher:notifySubscriber(?callback, xt:sparql(?query));\n" +
        "    } else {\n" + 
        "      xt:print(\"Not triggered\")\n" + 
        "    }\n" + 
        "  }\n" + 
        "}";
    
    String query = "prefix dispatcher: <function://org.hyperagents.rdfsub.NotificationDispatcher>" 
        + "@event\n" + updateMethod + " {" + quad + "}\n\n" + processFunction;
    
    try {
      QueryProcess.create(graph).sparqlUpdate(query);
    } catch (EngineException e) {
      LOGGER.debug(e.getMessage());
    }
  }
  
  private void processSubscription(String subscription) {
    // TODO: check that the SPARQL query is authorized to access the specified datasets
    
    Optional<String> callbackIri = getObjectAsString(subscription, Loader.TURTLE_FORMAT, "us:callback");
    Optional<String> triggerIri = getObjectAsString(subscription, Loader.TURTLE_FORMAT, "us:trigger");
    
    if (!callbackIri.isPresent() || !triggerIri.isPresent()) {
      return;
    }
    
    Future<Void> validCallbackFuture = Future.future(promise -> {
      WebClient webClient = WebClient.create(vertx);
      webClient.getAbs(callbackIri.get()).send(ar -> {
        if (ar.succeeded()) {
          if (ar.result().statusCode() == 204) {
            promise.complete();
          } else {
            promise.fail("Invalid status code: " + ar.result().statusCode());
          }
        } else {
          promise.fail("Callback IRI is unreachable.");
        }
      });
    });
    
    LOGGER.info("Retrieving the trigger function: " + triggerIri);
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
          
          String query = "insert data "
              + "{graph <" + SUBSCRIBER_GRAPH_IRI + "> { " + registration + "}}";
          
          QueryProcess.create(graph).sparqlUpdate(query);
          LOGGER.info("Subscription saved successfully: " + subscriptionIRI);
        } catch (EngineException e) {
          LOGGER.debug(e.getMessage());
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
      candidateIRI = SUBSCRIBER_GRAPH_IRI.concat(UUID.randomUUID().toString());
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
