package org.hyperagents.rdfsub;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hyperagents.rdfsub.ldscript.NotificationDispatcher;
import org.hyperagents.rdfsub.ldscript.Sandbox;

import fr.inria.corese.compiler.eval.Interpreter;
import fr.inria.corese.core.Graph;
import fr.inria.corese.core.api.Loader;
import fr.inria.corese.core.load.Load;
import fr.inria.corese.core.load.LoadException;
import fr.inria.corese.core.print.ResultFormat;
import fr.inria.corese.core.query.QueryProcess;
import fr.inria.corese.kgram.api.core.ExpType;
import fr.inria.corese.kgram.core.Mappings;
import fr.inria.corese.sparql.api.IDatatype;
import fr.inria.corese.sparql.datatype.DatatypeMap;
import fr.inria.corese.sparql.exceptions.EngineException;
import fr.inria.corese.sparql.exceptions.SafetyException;
import fr.inria.corese.sparql.exceptions.UndefinedExpressionException;
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
 * @author Andrei Ciortea, Interactions HSG
 *
 */
public class CoreseVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(CoreseVerticle.class.getName());
  
  private static final String DISPATCHER_PREFIX_DEFINITION = "prefix dispatcher: "
      + "<function://org.hyperagents.rdfsub.ldscript.NotificationDispatcher>\n";
  
  private static final String SANDBOX_PREFIX_DEFINITION = "prefix sandbox: "
      + "<function://org.hyperagents.rdfsub.ldscript.Sandbox>\n";
  
  private Graph graph;
  private String registryGraphURI;
  private CapabilityURIGenerator generator;
  
  private String updateFunction = "@update function us:processRegisteredQueries(q, del, ins) { true } ";
  
  @Override
  public void start() throws LoadException {
    graph = Graph.create();
    Sandbox.getInstance(graph);
    generator = new CapabilityURIGenerator(config());
    
    String updateFunPath = config().getString("process-queries-function", 
        "src/resources/processRegisteredQueries.rq");
    updateFunction = vertx.fileSystem().readFileBlocking(updateFunPath).toString();
    
    // The provided template does not contain the name of the graph of subscribers
    registryGraphURI = generator.generateCapabilityURI("/metadata/");
    updateFunction = updateFunction.replaceFirst("##SUBSCRIBERS_GRAPH_IRI##", registryGraphURI);
    
    vertx.eventBus().consumer("corese", this::handleRequest);
  }
  
  private void handleRequest(Message<String> message) {
    String method = message.headers().get("method");
    
    switch (method) {
      case "subscribe":
        processSubscription(message.body());
        break;
      case "create-topic":
        Optional<String> topicIRI = createResource("us:Topic", "/topics/", message.body());
        if (topicIRI.isPresent()) {
          message.reply(topicIRI.get());
        } else {
          message.fail(500, "Unable to create topic");
        }
        break;
      case "insert":
        updateTriple("insert data", message.body());
        LOGGER.info("Triple inserted: " + message.body());
        break;
      case "delete":
        updateTriple("delete data", message.body());
        LOGGER.info("Triple deleted: " + message.body());
        break;
      case "update-query":
        updateQuery(message.body());
        LOGGER.info("Update query: " + message.body());
        break;
      default:
        break;
    }

  }
  
  private void updateTriple(String updateMethod, String quad) {
    String query = updateMethod + " {" + quad + "}";
    updateQuery(query);
  }
  
  private void updateQuery(String query) {
    vertx.executeBlocking(promise -> {
      try {
        LOGGER.info("Performing query: " + query);
        QueryProcess.create(graph).sparqlUpdate(SANDBOX_PREFIX_DEFINITION 
            + DISPATCHER_PREFIX_DEFINITION
            + "@event\n" + query + "\n\n" 
            + updateFunction
            );
        promise.complete();
      } catch (EngineException e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.failed()) {
        LOGGER.info("Sending notifications failed: " + res.cause());
      }
    });
  }
  
  private void processSubscription(String subscription) {
    // TODO: check that the SPARQL query is authorized to access the specified datasets
    Optional<String> subscriptionIri = getSubjectAsString(subscription, Loader.TURTLE_FORMAT, 
        "us:Subscription");
    Optional<String> callbackIri = getObjectAsString(subscription, Loader.TURTLE_FORMAT, "us:callback");
    Optional<String> triggerIri = getObjectAsString(subscription, Loader.TURTLE_FORMAT, "us:trigger");
    Optional<String> query = getObjectAsString(subscription, Loader.TURTLE_FORMAT, "us:query");
    
    if (!subscriptionIri.isPresent() || !callbackIri.isPresent() || !triggerIri.isPresent() 
        || !query.isPresent()) {
      return;
    }
    
    Future<Void> validCallbackFuture = Future.future(promise -> {
      WebClient webClient = WebClient.create(vertx);
      
      LOGGER.info("Checking callback URL: " + callbackIri.get());
      
      webClient.getAbs(callbackIri.get()).send(ar -> {
        if (ar.succeeded()) {
          if (ar.result().statusCode() == 204) {
            promise.complete();
          } else {
            promise.fail("Callback test returned status code: " + ar.result().statusCode());
          }
        } else {
          promise.fail("Callback IRI is unreachable.");
        }
      });
    });
    
    LOGGER.info("New subscription requested with triggering function: " + triggerIri);
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
                  
                  // Remove the triggering function if it was already exported
                  if (Interpreter.getExtension().get(triggerIri.get()) != null) {
                    Interpreter.getExtension().removeNamespace(triggerIri.get());
                  }
                  
                  Sandbox sandbox = new Sandbox(Graph.create());
                  sandbox.query(response.body());
                  
                  IDatatype result = sandbox.invokeTrigger(triggerIri.get(),
                      DatatypeMap.createList(), DatatypeMap.createList());
                  
                  if (result == null) {
                    LOGGER.info("The trigger function is invalid.");
                    promise.fail("The trigger function is invalid.");
                  } else if (!result.isBoolean()) {
                    LOGGER.info("The trigger function does not return a boolean. Returned value was: " 
                        + result);
                    promise.fail("The trigger function does not return a boolean.");
                  } else {
                    promise.complete();
                  }
                
                } catch (SafetyException e) {
                  LOGGER.info("The trigger raised a security exception: " + e.getMessage());
                  promise.fail(e);
                } catch (UndefinedExpressionException e) {
                  LOGGER.info("The trigger calls a Linked Function that is either not defined or "
                      + "not authorized: " + e.getMessage());
                  promise.fail(e);
                } catch (EngineException e) {
                  LOGGER.info(e.getMessage());
                  promise.fail(e);
                }
              } else {
                promise.fail("Unsupported media type: " + response.getHeader("Content-Type"));
              }
            } else {
              LOGGER.info("Retrieving trigger function failed with status code: " 
                  + response.statusCode());
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
        createResource(subscriptionIri.get(), "us:Subscription", "/subscriptions/", subscription);
        
        try {
          Mappings solution = QueryProcess.create(graph).query(query.get());
          
          String notification = ResultFormat.format(solution).toString();
          String datatype = (solution.getGraph() == null) ? ExpType.DT + "mappings"
              : IDatatype.GRAPH_DATATYPE;
          
          NotificationDispatcher.sendHTTPNotification(subscriptionIri.get(), callbackIri.get(), 
              datatype, notification);
        } catch (EngineException e) {
          LOGGER.debug(e.getMessage());
        }
      } else {
        LOGGER.info("Subscription request failed: " + ar.cause().getMessage());
      }
    });
  }
  
  private Optional<String> createResource(String classIRI, String containerPath, 
      String representation) {
    List<String> resources = getAllResources(classIRI);
    String resourceIRI = generator.generateUniqueCapabilityURI(containerPath, resources);
    
    // The subscription to be created is identified by a null relative URI
    representation = representation.replaceAll("<>", "<" + resourceIRI + ">");
    
    return createResource(resourceIRI, classIRI, containerPath, representation);
  }
  
  private Optional<String> createResource(String resourceIRI, String classIRI, String containerPath, 
      String representation) {
    // TODO: validate payload
    try {
      String query = "insert data "
          + "{graph <" + registryGraphURI + "> { " + representation + "}}";
      
      QueryProcess.create(graph).sparqlUpdate(query);
      LOGGER.info("Resource created successfully: " + representation);
    
      return Optional.of(resourceIRI);
    } catch (EngineException e) {
      LOGGER.debug(e.getMessage());
    }
    
    return Optional.empty();
  }
  
  /**
   * Retrieves the URIs of all existing subscriptions.
   * 
   * @return the list of URIs as string values
   */
  private List<String> getAllResources(String classURI) {
    List<String> resources = new ArrayList<String>();
    
    String subQuery = "select ?resource from <" + registryGraphURI 
        + "> where { ?resource a " + classURI + " }";
    
    try {
      Mappings result = QueryProcess.create(graph).query(subQuery);
      
      result.forEach(mapping -> {
        resources.add(mapping.getValue("?resource").stringValue());
      });
    } catch (EngineException e) {
      LOGGER.debug(e.getMessage());
    }
    
    return resources;
  }
  
  private Optional<String> getObjectAsString(String representation, int format, String prop) {
    String query = "select ?object where { ?subject " + prop + " ?object }";
    
    return getOneAsString(representation, format, query, "?object");
  }
  
  private Optional<String> getSubjectAsString(String representation, int format, 
      String subjectClass) {
    String query = "select ?object where { ?subject a " + subjectClass + " }";
    
    return getOneAsString(representation, format, query, "?subject");
  }
  
  private Optional<String> getOneAsString(String representation, int format, String query, 
      String variableName) {
    try {
      Graph data = Graph.create();
      Load.create(data).loadString(representation, format);
      QueryProcess exec = QueryProcess.create(data);
      
      String value = exec.query(query).getValue(variableName).stringValue();
      
      return (value == null || value.isEmpty()) ? Optional.empty() : Optional.of(value);
      
    } catch (LoadException e) {
      LOGGER.debug(e.getMessage());
    } catch (EngineException e) {
      LOGGER.debug(e.getMessage());
    }
    
    return Optional.empty();
  }
  
}
