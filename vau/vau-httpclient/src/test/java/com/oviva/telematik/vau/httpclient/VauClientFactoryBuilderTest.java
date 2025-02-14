package com.oviva.telematik.vau.httpclient;

import static org.junit.jupiter.api.Assertions.*;

import com.oviva.telematik.vau.httpclient.internal.ConnectionFactory;
import com.oviva.telematik.vau.httpclient.internal.cert.TrustValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VauClientFactoryBuilderTest {

  @Mock private HttpClient mockHttpClient;
  @Mock private TrustValidator mockTrustValidator;

  private static final String TEST_USER_AGENT = "Test-UserAgent/1.0";

  @Test
  void newBuilder_shouldCreateNewInstance() {
    // Given & When
    var builder = VauClientFactoryBuilder.newBuilder();

    // Then
    assertNotNull(builder);
  }

  @Test
  void build_shouldCreateFactoryWithDefaultValues() {
    // Given
    var builder =
        VauClientFactoryBuilder.newBuilder()
            .xUserAgent(TEST_USER_AGENT)
            .withInsecureTrustValidator();

    // When
    var factory = builder.build();

    // Then
    assertNotNull(factory);
    assertInstanceOf(ConnectionFactory.class, factory);
  }

  @Test
  void outerClient_shouldSetAndReturnBuilder() {
    // Given
    var builder = VauClientFactoryBuilder.newBuilder();

    // When
    var result = builder.outerClient(mockHttpClient);

    // Then
    assertSame(builder, result);

    var factory = builder.xUserAgent(TEST_USER_AGENT).withInsecureTrustValidator().build();
    assertNotNull(factory);
  }

  @Test
  void isPu_shouldSetAndReturnBuilder() {
    // Given
    var builder = VauClientFactoryBuilder.newBuilder();

    // When
    var result = builder.isPu(false);

    // Then
    assertSame(builder, result);

    var factory =
        builder
            .outerClient(mockHttpClient)
            .xUserAgent(TEST_USER_AGENT)
            .withInsecureTrustValidator()
            .build();
    assertNotNull(factory);
  }

  @Test
  void trustValidator_shouldSetAndReturnBuilder() {
    // Given
    var builder = VauClientFactoryBuilder.newBuilder();

    // When
    var result = builder.trustValidator(mockTrustValidator);

    // Then
    assertSame(builder, result);

    var factory = builder.outerClient(mockHttpClient).xUserAgent(TEST_USER_AGENT).build();
    assertNotNull(factory);
  }

  @Test
  void xUserAgent_shouldSetAndReturnBuilder() {
    // Given
    var builder = VauClientFactoryBuilder.newBuilder();

    // When
    var result = builder.xUserAgent(TEST_USER_AGENT);

    // Then
    assertSame(builder, result);

    var factory = builder.outerClient(mockHttpClient).withInsecureTrustValidator().build();
    assertNotNull(factory);
  }

  @Test
  void withInsecureTrustValidator_shouldSetAndReturnBuilder() {
    // Given
    var builder = VauClientFactoryBuilder.newBuilder();

    // When
    var result = builder.withInsecureTrustValidator();

    // Then
    assertSame(builder, result);

    var factory = builder.outerClient(mockHttpClient).xUserAgent(TEST_USER_AGENT).build();
    assertNotNull(factory);
  }

  @Test
  void build_shouldThrowExceptionWhenOuterClientIsMissing() {
    // Given
    var builder =
        VauClientFactoryBuilder.newBuilder()
            .xUserAgent(TEST_USER_AGENT)
            .withInsecureTrustValidator();

    builder.outerClient(null);

    // When & Then
    var exception = assertThrows(IllegalArgumentException.class, builder::build);
    assertEquals("outer client missing", exception.getMessage());
  }

  @Test
  void build_shouldThrowExceptionWhenTrustValidatorIsMissing() {
    // Given
    var builder =
        VauClientFactoryBuilder.newBuilder()
            .outerClient(mockHttpClient)
            .xUserAgent(TEST_USER_AGENT);

    // When & Then
    var exception = assertThrows(IllegalArgumentException.class, builder::build);
    assertEquals("trust validator missing", exception.getMessage());
  }

  @Test
  void build_shouldThrowExceptionWhenXUserAgentIsMissing() {
    // Given
    var builder =
        VauClientFactoryBuilder.newBuilder()
            .outerClient(mockHttpClient)
            .withInsecureTrustValidator();

    // When & Then
    var exception = assertThrows(IllegalArgumentException.class, builder::build);
    assertEquals("xUserAgent missing", exception.getMessage());
  }

  @Test
  void build_shouldCreateWorkingFactoryWithAllPropertiesSet() {
    // Given
    var builder =
        VauClientFactoryBuilder.newBuilder()
            .outerClient(mockHttpClient)
            .xUserAgent(TEST_USER_AGENT)
            .trustValidator(mockTrustValidator)
            .isPu(false);

    // When
    var factory = builder.build();

    // Then
    assertNotNull(factory);
    assertInstanceOf(ConnectionFactory.class, factory);
  }
}
