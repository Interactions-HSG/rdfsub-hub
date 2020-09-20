package org.hyperagents.rdfsub;

import org.hyperagents.rdfsub.api.HttpAPIVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;

/**
 * Main entry point of an RDFSub Hub.
 * 
 * @author Andrei Ciortea, Interactions HSG, University of St. Gallen
 *
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() {
    vertx.deployVerticle(new HttpAPIVerticle(), new DeploymentOptions().setConfig(config()));
    
    vertx.deployVerticle(new CoreseVerticle(), new DeploymentOptions().setConfig(config()));
  }

}
