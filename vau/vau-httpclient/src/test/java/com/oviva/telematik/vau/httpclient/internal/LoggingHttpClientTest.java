package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

@ExtendWith(MockitoExtension.class)
class LoggingHttpClientTest {

  @Mock private HttpClient delegateClient;
  @Mock private Logger mockLogger;
  @Mock private LoggingEventBuilder logBuilder;
  @Mock private HttpResponse mockResponse;

  @InjectMocks private LoggingHttpClient loggingClient;

  private HttpRequest testRequest;
  private static final URI TEST_URI = URI.create("https://example.com/api");
  private static final byte[] TEST_BODY = "test body".getBytes(StandardCharsets.UTF_8);

  @BeforeEach
  void setUp() {
    testRequest =
        new HttpRequest(
            TEST_URI,
            "GET",
            List.of(new HttpHeader("Content-Type", "application/json")),
            TEST_BODY);
  }

  private void configureMockResponse() {
    when(mockResponse.status()).thenReturn(200);
    when(mockResponse.body()).thenReturn("response body".getBytes(StandardCharsets.UTF_8));
    when(mockResponse.headers())
        .thenReturn(List.of(new HttpHeader("Content-Type", "application/json")));
  }

  @Test
  void whenDebugDisabled_shouldNotLog() {
    // Given
    when(mockLogger.isDebugEnabled()).thenReturn(false);
    when(delegateClient.call(testRequest)).thenReturn(mockResponse);

    // When
    var response = loggingClient.call(testRequest);

    // Then
    assertSame(mockResponse, response);
    verify(delegateClient).call(testRequest);
    verify(mockLogger, never()).atDebug();
  }

  @Test
  void whenDebugEnabled_shouldLogRequestAndResponse() {
    // Given
    configureMockResponse();
    when(mockLogger.isDebugEnabled()).thenReturn(true);
    when(mockLogger.atDebug()).thenReturn(logBuilder);
    when(delegateClient.call(testRequest)).thenReturn(mockResponse);

    // When
    var response = loggingClient.call(testRequest);

    // Then
    assertSame(mockResponse, response);
    verify(delegateClient).call(testRequest);

    // Verify request logging - capture format string and arguments
    var formatCaptor = ArgumentCaptor.forClass(String.class);
    var argsCaptor = ArgumentCaptor.forClass(Object[].class);
    verify(logBuilder, times(2)).log(formatCaptor.capture(), argsCaptor.capture());

    var requestFormat = formatCaptor.getValue();
    var requestArgs = argsCaptor.getValue();

    assertTrue(requestFormat.contains("< http response:"));
    assertEquals("GET", requestArgs[0]);
    assertEquals(TEST_URI, requestArgs[1]);
    assertTrue(requestArgs[2].toString().contains("status=200"));

    // Verify response logging (first call will be request logging)
    var allFormats = formatCaptor.getAllValues();
    var allArgs = argsCaptor.getAllValues();

    // First capture is the request
    assertTrue(allFormats.get(0).contains("> http request:"));
    assertEquals("GET", allArgs.get(0)[0]);
    assertEquals(TEST_URI, allArgs.get(0)[1]);
    // Don't cast to byte[] as it's already a String in the captured arguments
    var requestLogContent = allArgs.get(0)[2].toString();
    assertTrue(requestLogContent.contains(new String(TEST_BODY, StandardCharsets.UTF_8)));
  }

  @Test
  void shouldPassUnchangedRequestToDelegate() {
    // Given
    when(mockLogger.isDebugEnabled()).thenReturn(true);
    when(mockLogger.atDebug()).thenReturn(logBuilder);
    when(delegateClient.call(any())).thenReturn(mockResponse);

    // Setup request with multiple headers
    var requestWithHeaders =
        new HttpRequest(
            TEST_URI,
            "POST",
            List.of(
                new HttpHeader("Content-Type", "application/json"),
                new HttpHeader("Authorization", "Bearer token"),
                new HttpHeader("X-Request-ID", "123")),
            TEST_BODY);

    // When
    loggingClient.call(requestWithHeaders);

    // Then
    // Capture the request passed to delegate
    var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(delegateClient).call(requestCaptor.capture());

    var capturedRequest = requestCaptor.getValue();
    assertEquals(TEST_URI, capturedRequest.uri());
    assertEquals("POST", capturedRequest.method());
    assertEquals(3, capturedRequest.headers().size());
    assertEquals(TEST_BODY, capturedRequest.body());
  }

  @Test
  void stringify_shouldFormatResponseCorrectly() {
    // Given
    when(mockLogger.isDebugEnabled()).thenReturn(true);
    when(mockLogger.atDebug()).thenReturn(logBuilder);

    // Create a response with multiple headers
    var headersForTest =
        List.of(
            new HttpHeader("Content-Type", "application/json"),
            new HttpHeader("Cache-Control", "no-cache"),
            new HttpHeader("X-Response-ID", "456"));

    var testBody = "{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8);
    var responseWithHeaders = new HttpResponse(201, headersForTest, testBody);

    when(delegateClient.call(testRequest)).thenReturn(responseWithHeaders);

    // When
    loggingClient.call(testRequest);

    // Then
    // Capture the logged response string
    var formatCaptor = ArgumentCaptor.forClass(String.class);
    var argsCaptor = ArgumentCaptor.forClass(Object[].class);
    verify(logBuilder, times(2)).log(formatCaptor.capture(), argsCaptor.capture());

    // Get the response log arguments (the second call)
    var responseArgs = argsCaptor.getAllValues().get(1);
    var loggedResponse = responseArgs[2].toString();

    // Verify response string contains all expected parts
    assertTrue(loggedResponse.contains("status=201"));
    assertTrue(loggedResponse.contains("Content-Type: application/json"));
    assertTrue(loggedResponse.contains("Cache-Control: no-cache"));
    assertTrue(loggedResponse.contains("X-Response-ID: 456"));
    assertTrue(loggedResponse.contains("{\"status\":\"success\"}"));
  }

  @Test
  void shouldHandleNullHeaders() {
    // Given
    when(mockLogger.isDebugEnabled()).thenReturn(true);
    when(mockLogger.atDebug()).thenReturn(logBuilder);

    // Create request with null headers
    var requestWithNullHeaders = new HttpRequest(TEST_URI, "GET", null, TEST_BODY);

    // Create response with null body for testing edge cases
    var responseWithNullBody = new HttpResponse(204, List.of(), null);

    when(delegateClient.call(requestWithNullHeaders)).thenReturn(responseWithNullBody);

    // When
    var response = loggingClient.call(requestWithNullHeaders);

    // Then
    assertEquals(204, response.status());
    assertNull(response.body());

    // Verify logging still happens
    verify(logBuilder, times(2)).log(any(String.class), any(Object[].class));
  }

  @Test
  void shouldHandleEmptyResponse() {
    // Given
    when(mockLogger.isDebugEnabled()).thenReturn(true);
    when(mockLogger.atDebug()).thenReturn(logBuilder);

    // Create response with empty body
    var emptyResponse = new HttpResponse(204, List.of(), new byte[0]);

    when(delegateClient.call(testRequest)).thenReturn(emptyResponse);

    // When
    loggingClient.call(testRequest);

    // Then
    // Verify logging contains empty body
    var formatCaptor = ArgumentCaptor.forClass(String.class);
    var argsCaptor = ArgumentCaptor.forClass(Object[].class);
    verify(logBuilder, times(2)).log(formatCaptor.capture(), argsCaptor.capture());

    // Get the response log arguments
    var responseArgs = argsCaptor.getAllValues().get(1);
    var loggedResponseForEmptyBody = responseArgs[2].toString();

    assertTrue(loggedResponseForEmptyBody.contains("status=204"));
  }
}
