package org.hyperagents.rdfsub;

import org.hyperagents.rdfsub.api.HttpAPIVerticle;

import io.vertx.core.AbstractVerticle;

/**
 * Main entry point of an RDFSub Hub.
 * 
 * @author Andrei Ciortea, Interactions HSG, University of St. Gallen
 *
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() {
    vertx.deployVerticle(new HttpAPIVerticle());
    vertx.deployVerticle(new CoreseVerticle());
  }

}
