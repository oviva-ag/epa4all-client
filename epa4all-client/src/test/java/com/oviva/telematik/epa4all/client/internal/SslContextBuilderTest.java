package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SslContextBuilderTest {

  @Mock private KeyStore mockKeyStore;

  @Test
  void buildSslContext_shouldCreateTlsv13Context() throws Exception {
    // Given
    when(mockKeyStore.aliases()).thenReturn(Collections.enumeration(List.of("test-alias")));

    // When
    var result = SslContextBuilder.buildSslContext(mockKeyStore);

    // Then
    assertNotNull(result);
    assertInstanceOf(SSLContext.class, result);
    assertEquals("TLSv1.3", result.getProtocol());
  }

  @Test
  void buildSslContext_shouldCreateValidSslContext() throws Exception {
    // Given
    when(mockKeyStore.aliases()).thenReturn(Collections.enumeration(List.of("test-alias")));

    // When
    var result = SslContextBuilder.buildSslContext(mockKeyStore);

    // Then
    assertNotNull(result);
    assertNotNull(result.getSocketFactory());
    assertNotNull(result.getServerSocketFactory());
  }

  @Test
  void sslContextBuilder_shouldNotBeInstantiable() {
    // When & Then - should not be able to instantiate utility class
    var constructors = SslContextBuilder.class.getDeclaredConstructors();
    assertEquals(1, constructors.length);

    var constructor = constructors[0];
    assertFalse(constructor.isAccessible());

    // Verify constructor is private
    assertEquals(0, constructor.getModifiers() & java.lang.reflect.Modifier.PUBLIC);
  }

  @Test
  void buildSslContext_shouldUseKeyStore() throws Exception {
    // Given
    when(mockKeyStore.aliases()).thenReturn(Collections.enumeration(List.of("test-alias")));

    // When
    SslContextBuilder.buildSslContext(mockKeyStore);

    // Then - verify the KeyStore was used
    verify(mockKeyStore).aliases();
  }
}
