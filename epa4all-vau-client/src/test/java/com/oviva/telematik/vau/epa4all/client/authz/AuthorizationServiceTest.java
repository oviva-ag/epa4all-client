package com.oviva.telematik.vau.epa4all.client.authz;

import static org.junit.jupiter.api.Assertions.*;

import com.oviva.telematik.vau.httpclient.HttpResponse;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {

  @Test
  void getNonce_includesErrorBodyInException() {
    var body = "{\"errorCode\":\"statusMismatch\",\"errorDetail\":\"already authorized\"}";
    var service =
        new AuthorizationService(
            req -> new HttpResponse(409, List.of(), body.getBytes()), null, null, null);

    var e =
        assertThrows(
            IllegalStateException.class,
            () -> service.getNonce(URI.create("https://epa-as-1.dev.epa4all.de"), "X123456789"));

    assertTrue(e.getMessage().contains("409"));
    assertTrue(e.getMessage().contains(body));
  }
}
