package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.oviva.telematik.vau.httpclient.HttpHeader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JavaHttpClientTest {

  @Mock private java.net.http.HttpClient mockJavaClient;
  @Mock private java.net.http.HttpResponse<byte[]> mockResponse;

  @InjectMocks private JavaHttpClient client;

  private static final URI TEST_URI = URI.create("https://example.com/api");
  private static final byte[] TEST_BODY = "test body".getBytes();

  @Test
  void from_returnsNewInstance() {
    // When
    var result = JavaHttpClient.from(mockJavaClient);

    // Then
    assertNotNull(result);
    assertInstanceOf(JavaHttpClient.class, result);
  }

  @Test
  void call_sendsGetRequestWithHeaders() throws IOException, InterruptedException {
    // Given
    var request =
        new com.oviva.telematik.vau.httpclient.HttpRequest(
            TEST_URI,
            "GET",
            List.of(
                new HttpHeader("Content-Type", "application/json"),
                new HttpHeader("Authorization", "Bearer token")),
            null);

    // Mock response
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("response".getBytes());

    HttpHeaders mockHeaders =
        HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (k, v) -> true);
    when(mockResponse.headers()).thenReturn(mockHeaders);

    // Capture the HttpRequest sent to the client
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockJavaClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    // When
    var response = client.call(request);

    // Then
    assertNotNull(response);
    assertEquals(200, response.status());

    // Verify the request
    var capturedRequest = requestCaptor.getValue();
    assertEquals(TEST_URI, capturedRequest.uri());
    assertEquals("GET", capturedRequest.method());

    // Verify headers
    assertTrue(capturedRequest.headers().firstValue("Content-Type").isPresent());
    assertEquals("application/json", capturedRequest.headers().firstValue("Content-Type").get());
    assertTrue(capturedRequest.headers().firstValue("Authorization").isPresent());
    assertEquals("Bearer token", capturedRequest.headers().firstValue("Authorization").get());
  }

  @Test
  void call_sendsPostRequestWithBody() throws IOException, InterruptedException {
    // Given
    var request =
        new com.oviva.telematik.vau.httpclient.HttpRequest(
            TEST_URI,
            "POST",
            List.of(new HttpHeader("Content-Type", "application/json")),
            TEST_BODY);

    // Mock response
    when(mockResponse.statusCode()).thenReturn(201);
    when(mockResponse.body()).thenReturn("created".getBytes());

    HttpHeaders mockHeaders =
        HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (k, v) -> true);
    when(mockResponse.headers()).thenReturn(mockHeaders);

    // Capture the HttpRequest sent to the client
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockJavaClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    // When
    var response = client.call(request);

    // Then
    assertNotNull(response);
    assertEquals(201, response.status());
    assertArrayEquals("created".getBytes(), response.body());

    // Verify the request
    var capturedRequest = requestCaptor.getValue();
    assertEquals(TEST_URI, capturedRequest.uri());
    assertEquals("POST", capturedRequest.method());

    // We can't directly verify the body in HttpRequest, but we can check that a non-empty
    // body publisher was used by checking its content length if available
    assertTrue(capturedRequest.bodyPublisher().isPresent());
    assertEquals(TEST_BODY.length, capturedRequest.bodyPublisher().get().contentLength());
  }

  @Test
  void call_handlesEmptyBody() throws IOException, InterruptedException {
    // Given
    var request =
        new com.oviva.telematik.vau.httpclient.HttpRequest(
            TEST_URI,
            "POST",
            List.of(new HttpHeader("Content-Type", "application/json")),
            new byte[0] // Empty body
            );

    // Mock response
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("ok".getBytes());
    when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

    // Capture the HttpRequest sent to the client
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockJavaClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    // When
    var response = client.call(request);

    // Then
    assertNotNull(response);

    // Verify the request uses empty body publisher
    var capturedRequest = requestCaptor.getValue();
    assertTrue(capturedRequest.bodyPublisher().isPresent());
    assertEquals(0, capturedRequest.bodyPublisher().get().contentLength());
  }

  @Test
  void call_handlesNullBody() throws IOException, InterruptedException {
    // Given
    var request =
        new com.oviva.telematik.vau.httpclient.HttpRequest(
            TEST_URI,
            "GET",
            List.of(new HttpHeader("Accept", "application/json")),
            null // Null body
            );

    // Mock response
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("ok".getBytes());
    when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

    // Capture the HttpRequest sent to the client
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockJavaClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    // When
    var response = client.call(request);

    // Then
    assertNotNull(response);

    // Verify the request uses empty body publisher
    var capturedRequest = requestCaptor.getValue();
    assertTrue(capturedRequest.bodyPublisher().isPresent());
    assertEquals(0, capturedRequest.bodyPublisher().get().contentLength());
  }

  @Test
  void call_handlesNullHeaders() throws IOException, InterruptedException {
    // Given
    var request =
        new com.oviva.telematik.vau.httpclient.HttpRequest(
            TEST_URI, "GET", null, // Null headers
            null);

    // Mock response
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("ok".getBytes());
    when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

    // Capture the HttpRequest sent to the client
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockJavaClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    // When
    var response = client.call(request);

    // Then
    assertNotNull(response);

    // Verify the request has no headers except system ones
    var capturedRequest = requestCaptor.getValue();
    assertEquals(0, capturedRequest.headers().map().size());
  }

  @Test
  void call_handlesIOException() throws IOException, InterruptedException {
    // Given
    var request =
        new com.oviva.telematik.vau.httpclient.HttpRequest(
            TEST_URI, "GET", List.of(new HttpHeader("Accept", "application/json")), null);

    // Mock IOException
    when(mockJavaClient.send(any(), any())).thenThrow(new IOException("Network error"));

    // When & Then
    var exception = assertThrows(HttpExceptionWithInfo.class, () -> client.call(request));

    // Verify exception details
    assertTrue(
        exception.getMessage().startsWith("http request failed"),
        "Exception message should start with expected prefix");
    assertInstanceOf(IOException.class, exception.getCause());
    assertEquals("Network error", exception.getCause().getMessage());
  }

  @Test
  void call_handlesInterruptedException() throws IOException, InterruptedException {
    // Given
    var request =
        new com.oviva.telematik.vau.httpclient.HttpRequest(
            TEST_URI, "GET", List.of(new HttpHeader("Accept", "application/json")), null);

    // Mock InterruptedException
    when(mockJavaClient.send(any(), any())).thenThrow(new InterruptedException("Interrupted"));

    // When
    var response = client.call(request);

    // Then
    assertNull(response); // Method returns null on interrupt
    assertTrue(Thread.currentThread().isInterrupted()); // Thread should be interrupted
  }

  @Test
  void call_convertsResponseHeaders() throws IOException, InterruptedException {
    // Given
    var request =
        new com.oviva.telematik.vau.httpclient.HttpRequest(
            TEST_URI, "GET", List.of(new HttpHeader("Accept", "application/json")), null);

    // Mock response with multiple headers
    HttpHeaders mockHeaders =
        HttpHeaders.of(
            Map.of(
                "Content-Type", List.of("application/json"),
                "Cache-Control", List.of("no-cache"),
                "X-Request-ID", List.of("123456")),
            (k, v) -> true);

    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("ok".getBytes());
    when(mockResponse.headers()).thenReturn(mockHeaders);

    when(mockJavaClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    // When
    var response = client.call(request);

    // Then
    assertNotNull(response);
    assertEquals(200, response.status());
    assertArrayEquals("ok".getBytes(), response.body());

    // Verify headers were converted correctly
    assertNotNull(response.headers());
    assertEquals(3, response.headers().size());

    assertTrue(
        response.headers().stream()
            .anyMatch(
                h -> "Content-Type".equals(h.name()) && "application/json".equals(h.value())));
    assertTrue(
        response.headers().stream()
            .anyMatch(h -> "Cache-Control".equals(h.name()) && "no-cache".equals(h.value())));
    assertTrue(
        response.headers().stream()
            .anyMatch(h -> "X-Request-ID".equals(h.name()) && "123456".equals(h.value())));
  }

  @Test
  void httpFailBadStatus_returnsHttpException() {
    // When
    var exception = JavaHttpClient.httpFailBadStatus("GET", TEST_URI, 404);

    // Then
    assertNotNull(exception);
    assertInstanceOf(HttpExceptionWithInfo.class, exception);
    assertTrue(exception.getMessage().contains("bad status"));
    assertEquals(404, ((HttpExceptionWithInfo) exception).status());
  }

  @Test
  void httpFailCausedBy_returnsHttpException() {
    // Given
    var cause = new RuntimeException("Something went wrong");

    // When
    var exception = JavaHttpClient.httpFailCausedBy("POST", TEST_URI, cause);

    // Then
    assertNotNull(exception);
    assertInstanceOf(HttpExceptionWithInfo.class, exception);
    assertTrue(
        exception.getMessage().startsWith("http request failed"),
        "Exception message should start with expected prefix");
    assertSame(cause, exception.getCause());
  }
}
