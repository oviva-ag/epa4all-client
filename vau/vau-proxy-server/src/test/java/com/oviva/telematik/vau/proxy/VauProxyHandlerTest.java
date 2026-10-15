package com.oviva.telematik.vau.proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import com.oviva.telematik.vau.httpclient.VauClientFactory;
import io.undertow.Undertow;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VauProxyHandlerTest {

  private Undertow server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void connectFails_returns502() throws Exception {
    var factory = mock(VauClientFactory.class);
    when(factory.connect(any())).thenThrow(new HttpClient.HttpException("handshake failed"));

    var res = get(start(factory));

    assertEquals(502, res.statusCode());
  }

  @Test
  void upstreamCallFails_returns502() throws Exception {
    var upstream = mock(HttpClient.class);
    when(upstream.call(any())).thenThrow(new HttpClient.HttpException("bad status code 403"));
    var factory = mock(VauClientFactory.class);
    when(factory.connect(any())).thenReturn(upstream);

    var res = get(start(factory));

    assertEquals(502, res.statusCode());
  }

  @Test
  void upstreamResponse_isForwarded() throws Exception {
    var upstream = mock(HttpClient.class);
    when(upstream.call(any()))
        .thenReturn(
            new HttpResponse(
                409, List.of(new HttpHeader("content-type", "application/json")), "{}".getBytes()));
    var factory = mock(VauClientFactory.class);
    when(factory.connect(any())).thenReturn(upstream);

    var res = get(start(factory));

    assertEquals(409, res.statusCode());
    assertEquals("{}", res.body());
    assertEquals("application/json", res.headers().firstValue("content-type").orElseThrow());
  }

  private URI start(VauClientFactory factory) {
    server =
        Undertow.builder()
            .addHttpListener(0, "localhost")
            .setHandler(new VauProxyHandler(factory))
            .build();
    server.start();
    var address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
    return URI.create("http://localhost:%d/epa/authz/v1/getNonce".formatted(address.getPort()));
  }

  private static java.net.http.HttpResponse<String> get(URI uri) throws Exception {
    try (var client = java.net.http.HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(uri).header("x-insurantid", "X123456789").GET().build(),
          java.net.http.HttpResponse.BodyHandlers.ofString());
    }
  }
}
