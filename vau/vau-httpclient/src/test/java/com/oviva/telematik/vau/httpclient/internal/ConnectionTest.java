package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import de.gematik.vau.lib.VauClientStateMachine;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionTest {

  private static final URI SESSION_URI = URI.create("https://epa-as-1.dev.epa4all.de/test-cid");

  @Mock private HttpClient outerClient;
  @Mock private VauClientStateMachine vauClient;

  private Connection connection;

  @BeforeEach
  void setUp() {
    connection = new Connection(outerClient, "/test-cid", SESSION_URI, vauClient);
    when(vauClient.encryptVauMessage(any())).thenReturn("ciphertext".getBytes());
  }

  @Test
  void call_decryptsResponse() {
    when(outerClient.call(any()))
        .thenReturn(new HttpResponse(200, List.of(), "encrypted".getBytes()));
    when(vauClient.decryptVauMessage("encrypted".getBytes())).thenReturn("plaintext".getBytes());

    assertArrayEquals("plaintext".getBytes(), connection.call("request".getBytes()));
  }

  @Test
  void call_includesVauErrorInException() throws Exception {
    var body =
        new CBORMapper()
            .writeValueAsBytes(
                Map.of("MessageType", "Error", "ErrorCode", 9, "ErrorMessage", "unknown CID"));
    when(outerClient.call(any())).thenReturn(new HttpResponse(403, List.of(), body));

    var e = assertThrows(HttpExceptionWithInfo.class, () -> connection.call("request".getBytes()));

    assertEquals(403, e.status());
    assertTrue(e.getMessage().contains("cid=/test-cid"), e.getMessage());
    assertTrue(e.getMessage().contains("\"ErrorCode\" : 9"), e.getMessage());
    assertTrue(e.getMessage().contains("unknown CID"), e.getMessage());
    verify(vauClient, never()).decryptVauMessage(any());
  }

  @Test
  void call_withoutErrorBody_throws() {
    when(outerClient.call(any())).thenReturn(new HttpResponse(500, List.of(), null));

    var e = assertThrows(HttpExceptionWithInfo.class, () -> connection.call("request".getBytes()));

    assertEquals(500, e.status());
    assertTrue(e.getMessage().contains("body: "), e.getMessage());
  }
}
