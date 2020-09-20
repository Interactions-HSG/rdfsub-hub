package org.hyperagents.rdfsub;

import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

/**
 * Generates capability URIs, that is URIs that are hard to guess. Such URIs are used to name resources
 * or to expose hard-to-guess endpoints. 
 * 
 * @see <a href="https://www.w3.org/TR/capability-urls/">Good Practices for Capability URLs (W3C Working Draft)</a>.  
 * 
 * @author Andrei Ciortea, Interactions HSG
 *
 */
public class CapabilityURIGenerator {
  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 8090;
  
  private final String BASE_URI;
  
  /**
   * Initializes the generator with a base URI constructed from the deployment configuration.  
   * 
   * @param config the configuration used to deploy the component; can be null or empty 
   */
  public CapabilityURIGenerator(JsonObject config) {
    if (config == null || config.getJsonObject("http") == null) {
      this.BASE_URI = "http://" + DEFAULT_HOST + ":" + DEFAULT_PORT;
    } else {
      JsonObject httpConfig = config.getJsonObject("http");
      String host = httpConfig.getString("virtual-host", DEFAULT_HOST);
      // If the port is not specified, we won't append it to the base URI
      Integer port = httpConfig.getInteger("virtual-port");
      
      if (port == null) {
        this.BASE_URI = "http://" + host;
      } else {
        this.BASE_URI = "http://" + host + ":" + DEFAULT_PORT;
      }
    }
  }
  
  /**
   * Generates a capability URI using the relative path "/".
   * 
   * @return the generated URI
   */
  public String generateCapabilityURI() {
    return generateCapabilityURI("/");
  }
  
  /**
   * Generates a capability URI with a given relative path (e.g., /subscribers/).
   * 
   * @param relativePath a relative path to be used for the generated URI 
   * @return the generated URI
   */
  public String generateCapabilityURI(String relativePath) {
    if (!relativePath.startsWith("/")) {
      relativePath = "/" + relativePath;
    }
    
    if (!relativePath.endsWith("/")) {
      relativePath += "/";
    }
    
    return BASE_URI.concat(relativePath)
        .concat(UUID.randomUUID().toString());
  }
  
  /**
   * Generates a capability URI and ensures it is not contained in a list of existing URIs.
   * 
   * @param existingURIs the list of existing URIs
   * @return the generated URI
   */
  public String generateUniqueCapabilityURI(List<String> existingURIs) {
    return generateUniqueCapabilityURI("/", existingURIs);
  }
  
  /**
   * Generates a capability URI with a given relative path and ensures it is not contained in a list 
   * of existing URIs.
   * 
   * @param existingURIs the list of existing URIs
   * @return the generated URI
   */
  public String generateUniqueCapabilityURI(String relativePath, List<String> existingURIs) {
    String candidateURI;
    
    do {
      candidateURI = generateCapabilityURI(relativePath);
    } while (existingURIs.contains(candidateURI));
    
    return candidateURI;
  }
}
