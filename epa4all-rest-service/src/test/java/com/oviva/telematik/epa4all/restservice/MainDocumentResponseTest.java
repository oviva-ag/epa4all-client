package com.oviva.telematik.epa4all.restservice;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oviva.telematik.epa4all.client.Epa4AllClientFactoryBuilder;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import java.net.InetSocketAddress;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MainDocumentResponseTest {

  private static final String BODY =
      "{\"content\":\"AAAA\",\"content_type\":\"application/fhir+xml\",\"insurant_id\":\"X123456789\"}";

  private final UUID documentId = UUID.randomUUID();

  private Undertow server;
  private String baseUri;

  @BeforeEach
  void setUp() throws Exception {
    var clientService = mock(Epa4allClientService.class);
    var response = new Epa4allClientService.WriteDocumentResponse(documentId);
    when(clientService.writeDocument(any(), any(), any())).thenReturn(response);
    when(clientService.replaceDocument(any(), any(), any(), any())).thenReturn(response);

    var buildHandler =
        Main.class.getDeclaredMethod("buildHandler", Epa4allClientService.class, long.class);
    buildHandler.setAccessible(true);
    var handler =
        (HttpHandler)
            buildHandler.invoke(
                new Main(null), clientService, Epa4AllClientFactoryBuilder.DEFAULT_MAX_BODY_BYTES);

    server = Undertow.builder().addHttpListener(0, "127.0.0.1").setHandler(handler).build();
    server.start();
    var port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
    baseUri = "http://127.0.0.1:" + port;
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void writeDocument_returns201WithJsonBody() {
    given()
        .baseUri(baseUri)
        .header("Content-Type", "application/json")
        .body(BODY)
        .post("/documents")
        .then()
        .statusCode(201)
        .contentType("application/json")
        .body("id", equalTo(documentId.toString()));
  }

  @Test
  void replaceDocument_returns201WithJsonBody() {
    given()
        .baseUri(baseUri)
        .header("Content-Type", "application/json")
        .body(BODY)
        .post("/documents/" + UUID.randomUUID())
        .then()
        .statusCode(201)
        .contentType("application/json")
        .body("id", equalTo(documentId.toString()));
  }
}
