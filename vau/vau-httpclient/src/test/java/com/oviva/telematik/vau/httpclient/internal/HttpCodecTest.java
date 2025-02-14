package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.*;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpCodecTest {

  private static final URI TEST_URI = URI.create("https://example.com/test");

  @Test
  void encode_shouldCreateValidHttpRequest_forGetWithNoBody() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI, "GET", List.of(new HttpHeader("Accept", "application/json")), null);

    // When
    var encoded = HttpCodec.encode(request);
    var encodedStr = new String(encoded, StandardCharsets.UTF_8);

    // Then
    assertTrue(encodedStr.startsWith("GET /test HTTP/1.1\r\n"), "Should start with request line");
    assertTrue(encodedStr.contains("Accept: application/json\r\n"), "Should contain header");
    assertTrue(
        encodedStr.contains("Host: example.com\r\n") || !encodedStr.contains("Host:"),
        "May or may not contain host header");
    assertTrue(encodedStr.contains("\r\n\r\n"), "Should have empty line before body");
  }

  @Test
  void encode_shouldCreateValidHttpRequest_forPostWithBody() {
    // Given
    var body = "test body".getBytes(StandardCharsets.UTF_8);
    var request =
        new HttpRequest(
            TEST_URI, "POST", List.of(new HttpHeader("Content-Type", "text/plain")), body);

    // When
    var encoded = HttpCodec.encode(request);
    var encodedStr = new String(encoded, StandardCharsets.UTF_8);

    // Then
    assertTrue(encodedStr.startsWith("POST /test HTTP/1.1\r\n"), "Should start with request line");
    assertTrue(encodedStr.contains("Content-Type: text/plain\r\n"), "Should contain header");
    assertTrue(encodedStr.contains("Content-Length: 9\r\n"), "Should add correct content length");
    assertTrue(encodedStr.endsWith("\r\n\r\ntest body"), "Should end with body");
  }

  @Test
  void encode_shouldCanonicalizeHeaderNames() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI,
            "GET",
            List.of(
                new HttpHeader("accept", "application/json"),
                new HttpHeader("CONTENT-TYPE", "text/plain")),
            null);

    // When
    var encoded = HttpCodec.encode(request);
    var encodedStr = new String(encoded, StandardCharsets.UTF_8);

    // Then
    assertTrue(
        encodedStr.contains("Accept: application/json\r\n"),
        "Should canonicalize lowercase header name");
    assertTrue(
        encodedStr.contains("CONTENT-TYPE: text/plain\r\n"),
        "Should preserve uppercase header name");
  }

  @Test
  void encode_shouldThrowExceptionForUnsupportedHeaders() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI,
            "GET",
            List.of(
                new HttpHeader("Accept", "application/json"),
                new HttpHeader("transfer-coding", "chunked")),
            null);

    // Then
    assertThrows(
        HttpClient.HttpException.class,
        () -> HttpCodec.encode(request),
        "Should throw exception for unsupported header");
  }

  @Test
  void encode_shouldSkipContentLengthHeader_whenAddedManually() {
    // Given
    var request =
        new HttpRequest(
            TEST_URI,
            "GET",
            List.of(new HttpHeader("Content-Length", "1000")),
            "test".getBytes(StandardCharsets.UTF_8));

    // When
    var encoded = HttpCodec.encode(request);
    var encodedStr = new String(encoded, StandardCharsets.UTF_8);

    // Then
    assertTrue(
        encodedStr.contains("Content-Length: 4\r\n"),
        "Should use actual content length, not the provided one");
    assertFalse(
        encodedStr.contains("Content-Length: 1000\r\n"),
        "Should not use manually provided Content-Length");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PATCH", "OPTIONS", "HEAD", "CONNECT", "TRACE"})
  void encode_shouldThrowException_forUnsupportedMethods(String method) {
    // Given
    var request = new HttpRequest(TEST_URI, method, Collections.emptyList(), null);

    // Then
    assertThrows(
        HttpClient.HttpException.class,
        () -> HttpCodec.encode(request),
        "Should throw exception for unsupported method: " + method);
  }

  @Test
  void encode_shouldThrowException_forInvalidHeaderName() {
    // Given
    var request =
        new HttpRequest(TEST_URI, "GET", List.of(new HttpHeader("Invalid Header", "value")), null);

    // Then
    assertThrows(
        HttpClient.HttpException.class,
        () -> HttpCodec.encode(request),
        "Should throw exception for invalid header name");
  }

  @Test
  void decode_shouldParseValidHttpResponse() {
    // Given
    var responseStr =
        """
        HTTP/1.1 200 OK\r
        Content-Type: application/json\r
        Content-Length: 13\r
        \r
        {"key":"value"}""";
    var responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);

    // When
    var response = HttpCodec.decode(responseBytes);

    // Then
    assertEquals(200, response.status(), "Should parse status code correctly");
    assertEquals(2, response.headers().size(), "Should parse headers correctly");

    // Verify Content-Type header
    boolean hasContentTypeHeader = false;
    for (HttpHeader header : response.headers()) {
      if ("Content-Type".equals(header.name()) && "application/json".equals(header.value())) {
        hasContentTypeHeader = true;
        break;
      }
    }
    assertTrue(hasContentTypeHeader, "Should have Content-Type header");

    assertEquals(
        "{\"key\":\"value\"}",
        new String(response.body(), StandardCharsets.UTF_8),
        "Should parse body correctly");
  }

  @Test
  void decode_shouldHandleResponseWithoutBody() {
    // Given
    var responseStr =
        """
        HTTP/1.1 204 No Content\r
        Server: Test\r
        Content-Length: 0\r
        \r
        """;
    var responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);

    // When
    var response = HttpCodec.decode(responseBytes);

    // Then
    assertEquals(204, response.status(), "Should parse status code correctly");
    assertEquals(2, response.headers().size(), "Should parse headers correctly");
    assertEquals(0, response.body().length, "Body should be empty");
  }

  @Test
  void decode_shouldHandleResponseWithMultipleHeaders() {
    // Given
    var responseStr =
        """
        HTTP/1.1 200 OK\r
        Content-Type: text/plain\r
        X-Custom-Header: value1\r
        X-Custom-Header: value2\r
        Content-Length: 4\r
        \r
        test""";
    var responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);

    // When
    var response = HttpCodec.decode(responseBytes);

    // Then
    assertEquals(200, response.status(), "Should parse status code correctly");
    assertEquals(4, response.headers().size(), "Should parse all headers correctly");

    // Check that both custom headers are present
    var customHeaderValues =
        response.headers().stream()
            .filter(h -> "X-Custom-Header".equals(h.name()))
            .map(HttpHeader::value)
            .toList();
    assertEquals(2, customHeaderValues.size(), "Should keep duplicate headers");
    assertTrue(customHeaderValues.contains("value1"), "Should contain first custom header value");
    assertTrue(customHeaderValues.contains("value2"), "Should contain second custom header value");

    assertEquals(
        "test", new String(response.body(), StandardCharsets.UTF_8), "Should parse body correctly");
  }

  @Test
  void decode_shouldHandleResponseWithoutContentLength() {
    // Given
    var responseStr =
        """
        HTTP/1.1 200 OK\r
        Content-Type: text/plain\r
        \r
        test body without explicit content length""";
    var responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);

    // When
    var response = HttpCodec.decode(responseBytes);

    // Then
    assertEquals(200, response.status(), "Should parse status code correctly");
    assertEquals(1, response.headers().size(), "Should parse header correctly");
    assertEquals(
        "test body without explicit content length",
        new String(response.body(), StandardCharsets.UTF_8),
        "Should parse body correctly even without Content-Length");
  }

  @Test
  void decode_shouldHandleResponseWithEmptyBody() {
    // Given
    var responseStr =
        """
        HTTP/1.1 200 OK\r
        Content-Type: application/json\r
        Content-Length: 0\r
        \r
        """;
    var responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);

    // When
    var response = HttpCodec.decode(responseBytes);

    // Then
    assertEquals(200, response.status(), "Should parse status code correctly");
    assertEquals(2, response.headers().size(), "Should parse headers correctly");
    assertEquals(0, response.body().length, "Body should be empty");
  }

  @Test
  void decode_shouldHandleMalformedStatusLine() {
    // Given
    var responseStr = "Malformed status line\r\nContent-Type: text/plain\r\n\r\nBody";
    var responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);

    // Then
    assertThrows(
        HttpClient.HttpException.class,
        () -> HttpCodec.decode(responseBytes),
        "Should throw exception for malformed status line");
  }

  @Test
  void decode_shouldHandleMalformedHeader() {
    // Given
    var responseStr =
        """
        HTTP/1.1 200 OK\r
        Malformed header without colon\r
        Content-Length: 4\r
        \r
        test""";
    var responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);

    // Then
    assertThrows(
        HttpClient.HttpException.class,
        () -> HttpCodec.decode(responseBytes),
        "Should throw exception for malformed header");
  }

  @Test
  void encode_decode() {
    // Given
    var body = "{\"test\":\"value\"}".getBytes(StandardCharsets.UTF_8);

    var responseBytes =
        """
        HTTP/1.1 200 OK\r
        Content-Type: application/json\r
        Content-Length: %d\r
        \r
        %s"""
            .formatted(body.length, new String(body, StandardCharsets.UTF_8))
            .getBytes(StandardCharsets.UTF_8);

    // Decode the response
    var decodedResponse = HttpCodec.decode(responseBytes);

    // Then
    assertEquals(200, decodedResponse.status());

    // Check Content-Type header
    boolean hasCorrectContentType = false;
    for (HttpHeader header : decodedResponse.headers()) {
      if ("Content-Type".equals(header.name()) && "application/json".equals(header.value())) {
        hasCorrectContentType = true;
        break;
      }
    }
    assertTrue(hasCorrectContentType, "Should preserve Content-Type header");

    // Compare bodies
    var originalBodyString = new String(body, StandardCharsets.UTF_8);
    var decodedBodyString = new String(decodedResponse.body(), StandardCharsets.UTF_8);
    assertEquals(originalBodyString, decodedBodyString, "Body should match original");
  }
}
