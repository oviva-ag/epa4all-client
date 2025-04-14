package com.oviva.telematik.epa4all.restservice;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.restassured.RestAssured;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// needs an actual konnektor running!
@Disabled("e2e")
class MainTest {

  private static final CountDownLatch exit = new CountDownLatch(1);

  private static Main app;

  @BeforeAll
  static void setUp() throws Exception {
    app = bootApp();
  }

  @AfterAll
  static void tearDown() throws Exception {
    exit.countDown();
    exit.await(5, TimeUnit.SECONDS);
  }

  @Test
  void bootsHealthy() {
    given().get("/health").then().statusCode(200);
  }

  @Test
  void writeDocument() {

    final var insurantId = "X110661675";

    var documentId = UUID.randomUUID();
    var content = loadDocument(documentId);
    given()
        .body(new CreateDocument(content, "application/fhir+xml", insurantId))
        .header("Content-Type", "application/json")
        .post("/documents")
        .then()
        .statusCode(200)
        .log()
        .all();
  }

  @Test
  void replaceDocument() {

    var documentId = UUID.randomUUID();
    final var insurantId = "X110661675";

    var content = loadDocument(documentId);
    given()
        .body(new CreateDocument(content, "application/fhir+xml", insurantId))
        .header("Content-Type", "application/json")
        .post("/documents")
        .then()
        .statusCode(200);
  }

  public record CreateDocument(
      @JsonProperty("content") byte[] content,
      @JsonProperty("content_type") String contentType,
      @JsonProperty("insurant_id") String insurantId) {}

  byte[] loadDocument(UUID id) {
    try (var is = MainTest.class.getResourceAsStream("/fhir_document.xml.template")) {
      var bytes = is.readAllBytes();
      var str = new String(bytes, StandardCharsets.UTF_8);
      return str.formatted(id).getBytes(StandardCharsets.UTF_8);
    } catch (IOException e) {
      fail(e);
      return null;
    }
  }

  private static Main bootApp() throws IOException {

    var config = new Properties();
    config.load(
        new StringReader(
            """
            konnektor.uri=https://10.156.145.103:443
            proxy.address=127.0.0.1
            port=0
            environment=RU
            log.level=DEBUG
            """));

    var executor = Executors.newFixedThreadPool(1);

    var started = new CountDownLatch(1);
    var appRef = new AtomicReference<Main>();
    executor.execute(
        () -> {
          try (var m = new Main(k -> Optional.ofNullable(config.getProperty(k)))) {
            m.run();
            appRef.set(m);
            started.countDown();
            exit.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    var ok = false;
    try {
      ok = started.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!ok) {
      fail("server failed to boot within timeout");
    }

    RestAssured.baseURI = "http://127.0.0.1:%d".formatted(appRef.get().listenerAddress().getPort());
    return appRef.get();
  }
}
