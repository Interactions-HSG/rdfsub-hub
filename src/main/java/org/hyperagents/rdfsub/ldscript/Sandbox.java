package org.hyperagents.rdfsub.ldscript;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fr.inria.corese.core.Graph;
import fr.inria.corese.core.query.QueryProcess;
import fr.inria.corese.sparql.api.IDatatype;
import fr.inria.corese.sparql.datatype.DatatypeMap;
import fr.inria.corese.sparql.triple.parser.Access;
import fr.inria.corese.sparql.triple.parser.Context;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * 
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
    this.graph = null;
  }
  
  /**
   * Instantiates an LDScript sandbox for a given graph. 
   * 
   * @param graph the graph to be used in the sandbox instance
   */
  public Sandbox(Graph graph) {
    this.graph = graph;
  }
  
  /**
   * This method is called from the LDScript processor.
   * 
   * @return an instance of the Sandbox if one was created, null otherwise
   */
  public static synchronized Sandbox singleton() {
    LOGGER.info("Singleton was called by LDScript processor!");
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
  
  public static void restrictAccess() {
    Access.set(Access.Feature.LINKED_FUNCTION, Access.Level.SUPER_USER);
    Access.set(Access.Feature.SPARQL_UPDATE, Access.Level.SUPER_USER);
    Access.set(Access.Feature.READ_WRITE_JAVA, Access.Level.SUPER_USER);
  }
  
  public static void relaxAccess() {
    Access.set(Access.Feature.LINKED_FUNCTION, Access.Level.PRIVATE);
    Access.set(Access.Feature.SPARQL_UPDATE, Access.Level.PRIVATE);
    Access.set(Access.Feature.READ_WRITE_JAVA, Access.Level.PRIVATE);
  }
  
  public IDatatype invokeTrigger(IDatatype trigger, IDatatype del, IDatatype ins) {
    return invokeTrigger(trigger.getLabel(), del, ins);
  }
  
  public IDatatype invokeTrigger(String trigger, IDatatype del, IDatatype ins) {
    LOGGER.info("Hello from sandbox! Graph is:\n" + graph);
    
    restrictAccess();
    
    ExecutorService exec = Executors.newSingleThreadExecutor();
    
    Future<IDatatype> result = exec.submit(new Callable<IDatatype>() {
      
      @Override
      public IDatatype call() throws Exception {
        Context context = new Context();
        context.setLevel(Access.Level.PUBLIC);
        
        return QueryProcess.create(graph).funcall(trigger, context, del, ins);
//        return QueryProcess.create(graph).funcall(trigger, del, ins);
      }
      
    });
    
    try {
      IDatatype value = result.get(1, TimeUnit.SECONDS);
      
      LOGGER.info("Returned value: " + value);
      
      if (value != null) {
        return value;
      }
    } catch (TimeoutException e) {
      LOGGER.info("Execution timed out for trigger: " + trigger);
      exec.shutdownNow();
      return DatatypeMap.FALSE;
    } catch (Exception e) {
      LOGGER.info("An exception was raised by invoking the trigger: " + trigger);
    } finally {
      relaxAccess();
      exec.shutdownNow();
    }
    
    return DatatypeMap.ERROR;
  }
  
}
