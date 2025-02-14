package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class UriMangleTest {

  @Test
  void downgradeHttpsUri_shouldConvertHttpsToHttp() {
    // Given
    var httpsUri = URI.create("https://example.com/path");

    // When
    var result = UriMangle.downgradeHttpsUri(httpsUri);

    // Then
    assertEquals("http", result.getScheme());
    assertEquals("example.com", result.getHost());
    assertEquals("/path", result.getPath());
  }

  @Test
  void downgradeHttpsUri_shouldConvertHttpsWithExplicitPort443ToHttp() {
    // Given
    var httpsUri = URI.create("https://example.com:443/path");

    // When
    var result = UriMangle.downgradeHttpsUri(httpsUri);

    // Then
    assertEquals("http", result.getScheme());
    assertEquals("example.com", result.getHost());
    assertEquals("/path", result.getPath());
  }

  @Test
  void downgradeHttpsUri_shouldReturnSameUriWhenAlreadyHttp() {
    // Given
    var httpUri = URI.create("http://example.com/path");

    // When
    var result = UriMangle.downgradeHttpsUri(httpUri);

    // Then
    assertEquals(httpUri, result);
  }

  @Test
  void downgradeHttpsUri_shouldThrowExceptionForNonHttpsWithNonStandardPort() {
    // Given
    var httpsNonStandardPortUri = URI.create("https://example.com:8443/path");

    // When & Then
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> UriMangle.downgradeHttpsUri(httpsNonStandardPortUri));

    assertEquals("failed downgrading uri=https://example.com:8443/path", exception.getMessage());
  }

  @Test
  void downgradeHttpsUri_shouldThrowExceptionForOtherSchemes() {
    // Given
    var ftpUri = URI.create("ftp://example.com/path");

    // When & Then
    var exception =
        assertThrows(IllegalArgumentException.class, () -> UriMangle.downgradeHttpsUri(ftpUri));

    assertEquals("failed downgrading uri=ftp://example.com/path", exception.getMessage());
  }

  @Test
  void downgradeHttpsUri_shouldHandleEmptyPaths() {
    // Given
    var httpsUri = URI.create("https://example.com");

    // When
    var result = UriMangle.downgradeHttpsUri(httpsUri);

    // Then
    assertEquals("http", result.getScheme());
    assertEquals("example.com", result.getHost());
    assertEquals("", result.getPath());
  }
}
