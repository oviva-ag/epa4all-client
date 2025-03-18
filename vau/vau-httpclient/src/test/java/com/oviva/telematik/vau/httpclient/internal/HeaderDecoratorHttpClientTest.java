package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeaderDecoratorHttpClientTest {

  @Mock private HttpClient mockDelegate;
  @Mock private HttpResponse mockResponse;

  private List<HttpHeader> extraHeaders;

  private HeaderDecoratorHttpClient client;

  @BeforeEach
  void setUp() {
    extraHeaders =
        List.of(
            new HttpHeader("X-Extra-Header", "extra-value"),
            new HttpHeader("Content-Type", "application/json"));
    client = new HeaderDecoratorHttpClient(mockDelegate, extraHeaders);
  }

  @Test
  void shouldAddExtraHeadersToRequest() {
    // Given
    var originalRequest =
        new HttpRequest(
            URI.create("https://example.com"),
            "GET",
            List.of(new HttpHeader("Accept", "text/html")),
            null);

    var expectedHeaders = new ArrayList<>(extraHeaders);
    expectedHeaders.add(new HttpHeader("Accept", "text/html"));

    var captor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockDelegate.call(captor.capture())).thenReturn(mockResponse);

    // When
    var response = client.call(originalRequest);

    // Then
    assertEquals(mockResponse, response);
    var capturedRequest = captor.getValue();
    assertEquals(3, capturedRequest.headers().size());
    assertEquals(expectedHeaders.size(), capturedRequest.headers().size());

    // Verify all expected headers are present
    for (HttpHeader expected : expectedHeaders) {
      boolean found =
          capturedRequest.headers().stream()
              .anyMatch(
                  h -> h.name().equals(expected.name()) && h.value().equals(expected.value()));
      assertEquals(true, found, "Expected header not found: " + expected);
    }
  }

  @Test
  void shouldReplaceExistingHeadersWithExtraHeaders() {
    // Given
    var originalRequest =
        new HttpRequest(
            URI.create("https://example.com"),
            "GET",
            List.of(
                new HttpHeader("Content-Type", "text/plain"), // Should be replaced
                new HttpHeader("Accept", "text/html") // Should be kept
                ),
            null);

    var captor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockDelegate.call(captor.capture())).thenReturn(mockResponse);

    // When
    var response = client.call(originalRequest);

    // Then
    assertEquals(mockResponse, response);
    var capturedRequest = captor.getValue();
    assertEquals(3, capturedRequest.headers().size());

    // Verify Content-Type is from extraHeaders (application/json)
    var contentType =
        capturedRequest.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .findFirst()
            .orElseThrow();
    assertEquals("application/json", contentType.value());

    // Verify Accept header is preserved
    var acceptHeader =
        capturedRequest.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Accept"))
            .findFirst()
            .orElseThrow();
    assertEquals("text/html", acceptHeader.value());
  }

  @Test
  void shouldHandleCaseInsensitiveHeaderReplacement() {
    // Given
    var originalRequest =
        new HttpRequest(
            URI.create("https://example.com"),
            "GET",
            List.of(
                new HttpHeader("content-type", "text/plain"), // lowercase version
                new HttpHeader("Accept", "text/html")),
            null);

    var captor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockDelegate.call(captor.capture())).thenReturn(mockResponse);

    // When
    var response = client.call(originalRequest);

    // Then
    assertEquals(mockResponse, response);
    var capturedRequest = captor.getValue();

    // Verify Content-Type is from extraHeaders despite case difference
    var contentTypeHeaders =
        capturedRequest.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .toList();
    assertEquals(1, contentTypeHeaders.size());
    assertEquals("application/json", contentTypeHeaders.get(0).value());
  }

  @Test
  void shouldHandleNullHeaders() {
    // Given
    var originalRequest = new HttpRequest(URI.create("https://example.com"), "GET", null, null);

    var captor = ArgumentCaptor.forClass(HttpRequest.class);
    when(mockDelegate.call(captor.capture())).thenReturn(mockResponse);

    // When
    var response = client.call(originalRequest);

    // Then
    assertEquals(mockResponse, response);
    var capturedRequest = captor.getValue();
    assertEquals(extraHeaders.size(), capturedRequest.headers().size());

    // Verify all extra headers are present
    for (HttpHeader expected : extraHeaders) {
      boolean found =
          capturedRequest.headers().stream()
              .anyMatch(
                  h -> h.name().equals(expected.name()) && h.value().equals(expected.value()));
      assertEquals(true, found, "Expected header not found: " + expected);
    }
  }
}
