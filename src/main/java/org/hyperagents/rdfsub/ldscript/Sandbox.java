package org.hyperagents.rdfsub.ldscript;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fr.inria.corese.core.Graph;
import fr.inria.corese.core.load.Load;
import fr.inria.corese.core.load.LoadException;
import fr.inria.corese.core.query.QueryProcess;
import fr.inria.corese.sparql.api.IDatatype;
import fr.inria.corese.sparql.exceptions.EngineException;
import fr.inria.corese.sparql.triple.parser.Access;
import fr.inria.corese.sparql.triple.parser.Context;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Utility class for sandboxing trigger functions. The functions run on isolated threads with 
 * limited functional and communication capabilities.
 * 
 * @author Andrei Ciortea, Interactions HSG
 *
 */
public class Sandbox {
  private static final Logger LOGGER = LoggerFactory.getLogger(Sandbox.class.getName());
  private static Sandbox instance = null;
  
  private final Graph graph;
  
  /**
   * Instantiates an empty LDScript Sandbox
   */
  public Sandbox() {
    this(null);
  }
  
  /**
   * Instantiates an LDScript sandbox for a given graph. 
   * 
   * @param graph the graph to be used in the sandbox instance
   */
  public Sandbox(Graph graph) {
    this.graph = graph;
    
    Access.set(Access.Feature.FUNCTION_DEFINITION, Access.Level.PUBLIC);
  }
  
  /**
   * This method is called from the LDScript processor.
   * 
   * @return an instance of the Sandbox if one was created, null otherwise
   */
  public static synchronized Sandbox singleton() {
    return instance;
  }
  
  /**
   * This method is called from the CoreseVerticle to create a Sandbox instance for a given graph.
   * 
   * @param graph the graph to be used in the sandbox instance
   * @return a sandbox instance for the given graph
   */
  public static synchronized Sandbox getInstance(Graph graph) {
    if (instance == null) {
      instance = new Sandbox(graph);
    }
    
    return instance; 
  }
  
  /**
   * Sets the access level for all LDScript features that can be accessed from triggering functions. 
   * Access levels are defined in Corese.
   * 
   * Corese features currently covered: LINKED_FUNCTION, SPARQL, SPARQL_UPDATE, READ_WRITE, 
   * JAVA_FUNCTION (see fr.inria.corese.sparql.triple.parser.Access.Feature).
   */
  public static void setAccessLevel(Access.Level level) {
    Access.set(Access.Feature.LINKED_FUNCTION, level);
    Access.set(Access.Feature.SPARQL, level);
    Access.set(Access.Feature.SPARQL_UPDATE, level);
    Access.set(Access.Feature.READ_WRITE, level);
    Access.set(Access.Feature.JAVA_FUNCTION, level);
  }
  
  /**
   * Loads RDF data or LDScript functions into the Corese instance of this sandbox. The content is 
   * loaded using the sandbox's default access restrictions (ATM specifying the load context is not 
   * available in Corese).
   * 
   * @param content the content to be loaded
   * @param format a Corese-specific int value that identifies the format (see fr.inria.corese.core.Load)
   * @throws LoadException
   */
  public void load(String content, int format) throws LoadException {
    setAccessLevel(Access.Level.SUPER_USER);
    Load.create(graph).loadString(content, format);
    setAccessLevel(Access.Level.PRIVATE);
  }
  
  /**
   * Runs a SPARQL query using a public access context (the lowest access level specified in Corese).
   * The query can include LDScript functions.
   * 
   * @param query the query represented as a string
   * @throws EngineException
   */
  public void query(String query) throws EngineException {
    QueryProcess.create(graph).query(query, createTriggerContext());
  }
  
  /**
   * Invokes a triggering function and returns the value. The invocation uses a public access context 
   * (the lowest access level specified in Corese).
   * 
   * @param trigger the triggering function
   * @param del the triples deleted with this data update
   * @param ins the triples inserted with this data update
   * @return value returned by the triggering function
   */
  public IDatatype invokeTrigger(IDatatype trigger, IDatatype del, IDatatype ins) {
    return invokeTrigger(trigger.getLabel(), del, ins);
  }
  
  /**
   * Invokes a triggering function and returns the value. The invocation uses a public access context 
   * (the lowest access level specified in Corese).
   * 
   * @param trigger the triggering function
   * @param del the triples deleted with this data update
   * @param ins the triples inserted with this data update
   * @return value returned by the triggering function
   */
  public IDatatype invokeTrigger(String trigger, IDatatype del, IDatatype ins) {
    ExecutorService exec = Executors.newSingleThreadExecutor();
    
    Future<IDatatype> result = exec.submit(new Callable<IDatatype>() {
      
      @Override
      public IDatatype call() throws Exception {
        return QueryProcess.create(graph).funcall(trigger, createTriggerContext(), del, ins);
      }
      
    });
    
    try {
      IDatatype value = result.get(1, TimeUnit.SECONDS);
      
      if (value != null) {
        return value;
      }
    } catch (TimeoutException e) {
      LOGGER.info("Execution timed out for trigger: " + trigger);
    } catch (Exception e) {
      LOGGER.info("An exception was raised by invoking the trigger: " + trigger);
    } finally {
      exec.shutdownNow();
    }
    
    return null;
  }
  
  private Context createTriggerContext() {
    Context context = new Context();
    context.setLevel(Access.Level.PUBLIC);
    return context;
  }
  
}
