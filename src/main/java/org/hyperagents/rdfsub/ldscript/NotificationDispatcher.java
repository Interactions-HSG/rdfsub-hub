package org.hyperagents.rdfsub.ldscript;

import fr.inria.corese.core.Graph;
import fr.inria.corese.core.print.ResultFormat;
import fr.inria.corese.core.transform.Transformer;
import fr.inria.corese.kgram.api.core.ExpType;
import fr.inria.corese.kgram.core.Mappings;
import fr.inria.corese.sparql.api.IDatatype;
import fr.inria.corese.sparql.datatype.DatatypeMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;

/**
 * Class used to dispatch notifications to subscribers. The current implementation sends the 
 * notifications to registered callback IRIs via HTTP POST. The payload of a notification is a result 
 * to a registered query:
 * - for a CONSTRUCT query, the payload is serialized and sent as `text/turtle`
 * - for a SELECT query, the payload is serialized and sent as `application/sparql-results+xml`
 * 
 * @author Andrei Ciortea, Interactions HSG, University of St. Gallen
 *
 */
public class NotificationDispatcher {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatcher.class.getName());
  
  /**
   * This method is called from LDscript to send notifications to subscribers whenever new results
   * are available for registered queries.
   * 
   * @param callbackIri the callback IRI registered by the subscriber
   * @param result latest result to the registered query
   * @return true if the result is a variable mapping or a graph, false otherwise 
   */
  public static IDatatype notifySubscriber(IDatatype subscriptionIri, IDatatype callbackIri, 
      IDatatype result) {
    String notification;
    
    // TODO: mappings data type constant?
    if (result.isLiteral() && result.getDatatypeURI().equals(ExpType.DT + "mappings")) {
      Mappings m = ((Mappings) result.getPointerObject());
      notification = ResultFormat.format(m).toString();
    } else if (result.isLiteral() && result.getDatatypeURI().equals(IDatatype.GRAPH_DATATYPE)) {
      Graph graph = (Graph) result.getPointerObject();
      notification = Transformer.turtle(graph);
    } else {
      return DatatypeMap.FALSE;
    }
    
    sendHTTPNotification(subscriptionIri.stringValue(), callbackIri.stringValue(), 
        result.getDatatypeURI(), notification);
    
    return DatatypeMap.TRUE;
  }
  
  public static void sendHTTPNotification(String subscriptionIri, String callbackIri, 
      String datatype, String payload) {
    WebClient webClient = WebClient.create(Vertx.currentContext().owner());
    HttpRequest<Buffer> request = webClient.postAbs(callbackIri);
    
    request.putHeader("Link", "<" + subscriptionIri + ">; rel=\"self\"");
    
    if (datatype.equals(IDatatype.GRAPH_DATATYPE)) {
      request.putHeader("Content-Type", "text/turtle");
    } else {
      request.putHeader("Content-Type", "application/sparql-results+xml");
    }
    
    request.sendBuffer(Buffer.buffer(payload), ar -> {
      if (ar.succeeded()) {
        int statusCode = ar.result().statusCode();
        
        if (statusCode >= 200 && statusCode < 300) {
          LOGGER.info("Sent notification to " + callbackIri + " with payload:\n" + payload);
        } else {
          LOGGER.info("Failed to send notification to " + callbackIri + " (status code: " 
              + statusCode + "). Payload:\n" + payload);
        }
      } else {
        LOGGER.info("Failed to send notification to " + callbackIri + " (unreachable). Payload:\n" 
            + payload);
      }
    });
  }
  
}
