package com.oviva.epa.client.konn.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;
import javax.net.ssl.TrustManager;
import org.junit.jupiter.api.Test;

class KonnektorConnectionFactoryImplTest {

  @Test
  void constructor_httpsUri_setsTlsPreferredTrue() {
    var factory = new KonnektorConnectionFactoryImpl(buildConfig("https://konnektor.example.com"));

    assertTrue(factory.isTlsPreferred);
  }

  @Test
  void constructor_httpUri_setsTlsPreferredFalse() {
    var factory = new KonnektorConnectionFactoryImpl(buildConfig("http://konnektor.example.com"));

    assertFalse(factory.isTlsPreferred);
  }

  @Test
  void constructor_uppercaseHttpsUri_setsTlsPreferredTrue() {
    var factory = new KonnektorConnectionFactoryImpl(buildConfig("HTTPS://konnektor.example.com"));

    assertTrue(factory.isTlsPreferred);
  }

  @Test
  void tlsClientParameters_withSubjectAlternativeName_setsStaticHostnameVerifier() {
    var factory =
        new KonnektorConnectionFactoryImpl(
            buildConfig("https://konnektor.example.com", "konnektor.konlan"));

    var params = factory.tlsClientParameters();

    assertNotNull(params.getHostnameVerifier());
    assertInstanceOf(StaticHostnameVerifier.class, params.getHostnameVerifier());
  }

  @Test
  void tlsClientParameters_withoutSubjectAlternativeName_hasNoCustomHostnameVerifier() {
    var factory = new KonnektorConnectionFactoryImpl(buildConfig("https://konnektor.example.com"));

    var params = factory.tlsClientParameters();

    assertNull(params.getHostnameVerifier());
  }

  @Test
  void tlsClientParameters_withTrustManagers_configuresTrustManagers() {
    var trustManager = mock(TrustManager.class);
    var factory =
        new KonnektorConnectionFactoryImpl(
            buildConfig("https://konnektor.example.com", null, List.of(trustManager)));

    var params = factory.tlsClientParameters();

    assertArrayEquals(new TrustManager[] {trustManager}, params.getTrustManagers());
  }

  @Test
  void tlsClientParameters_emptyTrustManagers_setsEmptyArray() {
    var factory = new KonnektorConnectionFactoryImpl(buildConfig("https://konnektor.example.com"));

    var params = factory.tlsClientParameters();

    assertNotNull(params.getTrustManagers());
    assertEquals(0, params.getTrustManagers().length);
  }

  // -- helpers --

  private static KonnektorConnectionConfiguration buildConfig(String uriStr) {
    return buildConfig(uriStr, null, List.of());
  }

  private static KonnektorConnectionConfiguration buildConfig(String uriStr, String san) {
    return buildConfig(uriStr, san, List.of());
  }

  private static KonnektorConnectionConfiguration buildConfig(
      String uriStr, String san, List<TrustManager> trustManagers) {
    var tlsConfig =
        new KonnektorConnectionConfiguration.TlsConfig(List.of(), trustManagers, List.of(), san);
    return new KonnektorConnectionConfiguration(URI.create(uriStr), tlsConfig, null, null);
  }
}
