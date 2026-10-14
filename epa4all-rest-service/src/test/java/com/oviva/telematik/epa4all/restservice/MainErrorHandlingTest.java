package com.oviva.telematik.epa4all.restservice;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.oviva.telematik.epa4all.client.ClientException;
import com.oviva.telematik.epa4all.client.DuplicateDocumentClientException;
import com.oviva.telematik.epa4all.client.EpaInformationServiceMalformedRequestClientException;
import com.oviva.telematik.epa4all.client.EpaInformationServiceUnavailableClientException;
import com.oviva.telematik.epa4all.client.EpaRecordUnavailableClientException;
import com.oviva.telematik.epa4all.client.NotAuthorizedClientException;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MainErrorHandlingTest {

  private Undertow server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void recordUnavailable_returns503WithRetryAfter24h() {
    var baseUri =
        start(
            () ->
                new EpaRecordUnavailableClientException(
                    "ePA record temporarily unavailable",
                    new RuntimeException("409 statusMismatch")));

    given()
        .baseUri(baseUri)
        .post("/documents")
        .then()
        .statusCode(503)
        .header("Retry-After", "86400")
        .contentType("application/json")
        .body("type", equalTo("/epa4all/epa-record-unavailable"))
        .body("title", equalTo("ePA Record Unavailable"))
        .body(
            "detail",
            equalTo(
                "The patient's ePA record is temporarily unavailable (relocation or maintenance)"));
  }

  @Test
  void informationServiceUnavailable_returns503WithRetryAfter10min() {
    var baseUri =
        start(
            () ->
                new EpaInformationServiceUnavailableClientException(
                    "failed to determine ePA record status",
                    new RuntimeException("500 internalError")));

    given()
        .baseUri(baseUri)
        .post("/documents")
        .then()
        .statusCode(503)
        .header("Retry-After", "600")
        .contentType("application/json")
        .body("type", equalTo("/epa4all/information-service-unavailable"))
        .body("title", equalTo("ePA Information Service Unavailable"))
        .body("detail", equalTo("Could not determine the status of the patient's ePA record"));
  }

  @Test
  void informationServiceMalformedRequest_returns500WithoutRetryAfter() {
    var baseUri =
        start(
            () ->
                new EpaInformationServiceMalformedRequestClientException(
                    "Information Service rejected the request as malformed",
                    new RuntimeException("400 malformedRequest")));

    given()
        .baseUri(baseUri)
        .post("/documents")
        .then()
        .statusCode(500)
        .header("Retry-After", nullValue())
        .contentType("application/json")
        .body("type", equalTo("/epa4all/internal-error"))
        .body("detail", not(containsString("X123456789")));
  }

  @Test
  void duplicateDocument_returns409() {
    var baseUri = start(() -> new DuplicateDocumentClientException("duplicate"));

    given()
        .baseUri(baseUri)
        .post("/documents")
        .then()
        .statusCode(409)
        .contentType("application/json")
        .body("type", equalTo("/epa4all/duplicate-document"));
  }

  @Test
  void notAuthorized_returns403() {
    var baseUri = start(() -> new NotAuthorizedClientException("not authorized"));

    given()
        .baseUri(baseUri)
        .post("/documents")
        .then()
        .statusCode(403)
        .contentType("application/json")
        .body("type", equalTo("/epa4all/not-entitled"));
  }

  @Test
  void genericClientException_returns500WithoutLeakingMessage() {
    var baseUri = start(() -> new ClientException("endpoint for KVNR X123456789 not found"));

    given()
        .baseUri(baseUri)
        .post("/documents")
        .then()
        .statusCode(500)
        .contentType("application/json")
        .body("type", equalTo("/epa4all/internal-error"))
        .body("detail", not(containsString("X123456789")));
  }

  @Test
  void badRequest_returns400WithoutExceptionMessage() {
    var baseUri = start(() -> new BadRequestException("invalid UUID"));

    given()
        .baseUri(baseUri)
        .post("/documents")
        .then()
        .statusCode(400)
        .contentType("application/json")
        .body("type", equalTo("/epa4all/bad-request"))
        .body("title", equalTo("Bad Request"))
        .body(
            "detail",
            equalTo(
                "The request is invalid, e.g. a required field is missing or a value has an"
                    + " invalid format"));
  }

  private String start(Supplier<RuntimeException> exception) {
    HttpHandler throwing =
        ex -> {
          throw exception.get();
        };
    server =
        Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(
                new BlockingHandler(new Main(name -> Optional.empty()).withErrorHandling(throwing)))
            .build();
    server.start();
    var address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
    return "http://127.0.0.1:" + address.getPort();
  }
}
