package org.hyperagents.rdfsub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperagents.rdfsub.api.HttpAPIVerticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MainVerticleTest {

  @BeforeEach
  void prepare(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(HttpAPIVerticle.class.getCanonicalName(), testContext.succeeding(id -> testContext.completeNow()));
  }

  @Test
  @DisplayName("Check that the server has started")
  void checkServerHasStarted(Vertx vertx, VertxTestContext testContext) {
    WebClient webClient = WebClient.create(vertx);
    webClient.get(8090, "localhost", "/")
      .as(BodyCodec.string())
      .send(testContext.succeeding(response -> testContext.verify(() -> {
        assertEquals(200, response.statusCode());
        assertTrue(response.body().length() > 0);
        assertTrue(response.body().contains("RDFSub Hub"));
        testContext.completeNow();
      })));
  }
  
  @Test
  @DisplayName("Test valid subscribe request over HTTP")
  void testHttpSubscribe(Vertx vertx, VertxTestContext testContext) {
    WebClient webClient = WebClient.create(vertx);
    webClient.post(8090, "localhost", "/subscription")
      .putHeader("Content-Type", "text/turtle")
      .sendBuffer(Buffer.buffer("<> a us:Subscription ;\n" + 
          "us:callback <http://localhost:8090/callback> ;\n" + 
          "us:trigger <http://localhost:8090/trigger> ;\n" + 
          "us:query \"select * where { ?x ?y ?z }\" ."), 
          testContext.succeeding(response -> testContext.verify(() -> {
        assertEquals(202, response.statusCode());
        testContext.completeNow();
      })));
  }
}
