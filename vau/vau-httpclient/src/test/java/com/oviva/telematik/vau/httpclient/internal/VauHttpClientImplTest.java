package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VauHttpClientImplTest {

  @Mock private Connection mockConnection;

  @InjectMocks private VauHttpClientImpl client;

  private static final URI TEST_URI = URI.create("https://example.com/api");
  private static final byte[] TEST_REQUEST_BODY =
      "test request body".getBytes(StandardCharsets.UTF_8);
  private static final byte[] TEST_RESPONSE_BODY =
      "test response body".getBytes(StandardCharsets.UTF_8);

  @Test
  void constructor_shouldInitializeWithConnection() {
    // Given & When & Then
    assertNotNull(client, "Client should be initialized");
  }

  @Test
  void call_shouldProcessRequestAndReturnResponse() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI, "GET", List.of(new HttpHeader("Accept", "application/json")), null);

    when(mockConnection.call(any())).thenReturn(createEncodedResponse());

    // When
    var response = client.call(request);

    // Then
    assertNotNull(response, "Response should not be null");
    verify(mockConnection).call(any());

    assertEquals(200, response.status());
    assertEquals(2, response.headers().size());

    var contentTypeHeader =
        response.headers().stream()
            .filter(h -> "Content-Type".equals(h.name()))
            .findFirst()
            .orElse(null);
    assertNotNull(contentTypeHeader, "Content-Type header should be present");
    assertEquals("application/json", contentTypeHeader.value());

    var contentLengthHeader =
        response.headers().stream()
            .filter(h -> "Content-Length".equals(h.name()))
            .findFirst()
            .orElse(null);
    assertNotNull(contentLengthHeader, "Content-Length header should be present");
    assertEquals(String.valueOf(TEST_RESPONSE_BODY.length), contentLengthHeader.value());

    assertArrayEquals(TEST_RESPONSE_BODY, response.body());
  }

  @Test
  void call_shouldProcessPostWithBody() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI,
            "POST",
            List.of(new HttpHeader("Content-Type", "application/json")),
            TEST_REQUEST_BODY);

    when(mockConnection.call(any())).thenReturn(createEncodedResponse());

    ArgumentCaptor<byte[]> requestBytesCaptor = ArgumentCaptor.forClass(byte[].class);

    // When
    var response = client.call(request);

    // Then
    verify(mockConnection).call(requestBytesCaptor.capture());
    byte[] capturedBytes = requestBytesCaptor.getValue();

    assertNotNull(capturedBytes);
    assertTrue(capturedBytes.length > 0, "Encoded request should have content");

    assertNotNull(response);
    assertEquals(200, response.status());
    assertArrayEquals(TEST_RESPONSE_BODY, response.body());
  }

  @Test
  void call_shouldAdjustContentLengthHeader() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI,
            "POST",
            List.of(
                new HttpHeader("Content-Type", "application/json"),
                new HttpHeader("Content-Length", "1000")),
            TEST_REQUEST_BODY);

    when(mockConnection.call(any())).thenReturn(createEncodedResponse());
    ArgumentCaptor<byte[]> requestBytesCaptor = ArgumentCaptor.forClass(byte[].class);

    // When
    var response = client.call(request);
    assertNotNull(response);

    // Then
    verify(mockConnection).call(requestBytesCaptor.capture());

    String encodedRequest = new String(requestBytesCaptor.getValue(), StandardCharsets.UTF_8);

    assertFalse(
        encodedRequest.contains("Content-Length: 1000"),
        "Manual Content-Length header should be removed");

    assertTrue(
        encodedRequest.contains("Content-Length: " + TEST_REQUEST_BODY.length),
        "Actual Content-Length header should be added");
  }

  @Test
  void call_shouldRemoveTransferEncodingChunkedHeader() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI,
            "POST",
            List.of(
                new HttpHeader("Content-Type", "application/json"),
                new HttpHeader("Transfer-Encoding", "chunked")),
            TEST_REQUEST_BODY);

    when(mockConnection.call(any())).thenReturn(createEncodedResponse());
    ArgumentCaptor<byte[]> requestBytesCaptor = ArgumentCaptor.forClass(byte[].class);

    // When
    var response = client.call(request);
    assertNotNull(response);

    // Then
    verify(mockConnection).call(requestBytesCaptor.capture());

    String encodedRequest = new String(requestBytesCaptor.getValue(), StandardCharsets.UTF_8);

    assertFalse(
        encodedRequest.contains("Transfer-Encoding"), "Transfer-Encoding header should be removed");
  }

  @Test
  void call_shouldHandleNullHeaders() {
    // Given
    var request = new HttpRequest(TEST_URI, "GET", null, null);

    when(mockConnection.call(any())).thenReturn(createEncodedResponse());

    // When
    var response = client.call(request);

    // Then
    assertNotNull(response);
    verify(mockConnection).call(any());
  }

  @Test
  void call_shouldHandleEmptyBody() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI,
            "POST",
            List.of(new HttpHeader("Content-Type", "application/json")),
            new byte[0]);

    when(mockConnection.call(any())).thenReturn(createEncodedResponse());
    ArgumentCaptor<byte[]> requestBytesCaptor = ArgumentCaptor.forClass(byte[].class);

    // When
    var response = client.call(request);

    // Then
    verify(mockConnection).call(requestBytesCaptor.capture());

    String encodedRequest = new String(requestBytesCaptor.getValue(), StandardCharsets.UTF_8);

    assertFalse(
        encodedRequest.contains("Content-Length:"),
        "Content-Length header should NOT be present for empty body");

    assertNotNull(response);
    assertEquals(200, response.status());
  }

  @Test
  void call_shouldPropagateExceptionFromConnection() {
    // Given
    var request = new HttpRequest(TEST_URI, "GET", null, null);
    var exception = new HttpClient.HttpException("Test exception");
    when(mockConnection.call(any())).thenThrow(exception);

    // When & Then
    var thrown =
        assertThrows(
            HttpClient.HttpException.class,
            () -> client.call(request),
            "Exception from Connection should be propagated");

    assertEquals(exception, thrown);
  }

  @Test
  void call_shouldFilterOutNullHeaderName() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI,
            "GET",
            List.of(new HttpHeader("Accept", "application/json"), new HttpHeader(null, "value")),
            null);

    when(mockConnection.call(any())).thenReturn(createEncodedResponse());
    ArgumentCaptor<byte[]> requestBytesCaptor = ArgumentCaptor.forClass(byte[].class);

    // When
    var response = client.call(request);
    assertNotNull(response);

    // Then
    verify(mockConnection).call(requestBytesCaptor.capture());

    var encodedRequest = new String(requestBytesCaptor.getValue(), StandardCharsets.UTF_8);

    assertTrue(
        encodedRequest.contains("Accept: application/json"), "Valid header should be included");
    assertFalse(encodedRequest.contains("null: value"), "Null header name should be filtered out");
  }

  private byte[] createEncodedResponse() {

    return """
    HTTP/1.1 200 OK\r
    Content-Type: application/json\r
    Content-Length: %s\r
    \r
    %s"""
        .formatted(
            TEST_RESPONSE_BODY.length, new String(TEST_RESPONSE_BODY, StandardCharsets.UTF_8))
        .getBytes(StandardCharsets.UTF_8);
  }
}
