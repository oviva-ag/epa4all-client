package com.oviva.telematik.epa4all.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;

import com.oviva.epa.client.KonnektorService;
import com.oviva.telematik.epa4all.client.internal.Epa4AllClientFactory;
import com.oviva.telematik.epa4all.client.internal.TelematikTrustRoots;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class Epa4AllClientFactoryBuilderTest {

  @Test
  void build_shouldRequireKonnektorService() {
    var builder = Epa4AllClientFactoryBuilder.newBuilder();

    assertThrows(NullPointerException.class, () -> builder.konnektorService(null).build());
  }

  @Test
  void build_shouldRequireEnvironment() {
    var builder = Epa4AllClientFactoryBuilder.newBuilder();

    assertThrows(
        NullPointerException.class,
        () -> builder.konnektorService(mock(KonnektorService.class)).environment(null).build());
  }

  @Test
  void build_shouldUseProvidedTrustStoreWhenSet() {
    var konnektorService = mock(KonnektorService.class);
    var providedTrustStore = mock(KeyStore.class);

    try (MockedStatic<TelematikTrustRoots> trustRoots =
            Mockito.mockStatic(TelematikTrustRoots.class);
        MockedStatic<Epa4AllClientFactory> factoryMock =
            Mockito.mockStatic(Epa4AllClientFactory.class)) {

      factoryMock
          .when(
              () ->
                  Epa4AllClientFactory.create(
                      eq(konnektorService),
                      isNull(),
                      eq(Environment.RU),
                      eq(providedTrustStore),
                      isNull()))
          .thenReturn(mock(Epa4AllClientFactory.class));

      var result =
          Epa4AllClientFactoryBuilder.newBuilder()
              .konnektorService(konnektorService)
              .environment(Environment.RU)
              .trustStore(providedTrustStore)
              .build();

      assertNotNull(result);

      trustRoots.verifyNoInteractions();
    }
  }

  @Test
  void build_shouldLoadRuTruststoreWhenNotProvidedAndEnvRu() {
    var konnektorService = mock(KonnektorService.class);
    var ruTrustStore = mock(KeyStore.class);

    try (MockedStatic<TelematikTrustRoots> trustRoots =
            Mockito.mockStatic(TelematikTrustRoots.class);
        MockedStatic<Epa4AllClientFactory> factoryMock =
            Mockito.mockStatic(Epa4AllClientFactory.class)) {

      trustRoots.when(TelematikTrustRoots::loadRuTruststore).thenReturn(ruTrustStore);

      factoryMock
          .when(
              () ->
                  Epa4AllClientFactory.create(
                      eq(konnektorService),
                      isNull(),
                      eq(Environment.RU),
                      eq(ruTrustStore),
                      isNull()))
          .thenReturn(mock(Epa4AllClientFactory.class));

      var result =
          Epa4AllClientFactoryBuilder.newBuilder()
              .konnektorService(konnektorService)
              .environment(Environment.RU)
              .build();

      assertNotNull(result);
    }
  }

  @Test
  void build_shouldLoadPuTruststoreWhenNotProvidedAndEnvPu() {
    var konnektorService = mock(KonnektorService.class);
    var puTrustStore = mock(KeyStore.class);

    try (MockedStatic<TelematikTrustRoots> trustRoots =
            Mockito.mockStatic(TelematikTrustRoots.class);
        MockedStatic<Epa4AllClientFactory> factoryMock =
            Mockito.mockStatic(Epa4AllClientFactory.class)) {

      trustRoots.when(TelematikTrustRoots::loadPuTruststore).thenReturn(puTrustStore);

      factoryMock
          .when(
              () ->
                  Epa4AllClientFactory.create(
                      eq(konnektorService),
                      isNull(),
                      eq(Environment.PU),
                      eq(puTrustStore),
                      isNull()))
          .thenReturn(mock(Epa4AllClientFactory.class));

      var result =
          Epa4AllClientFactoryBuilder.newBuilder()
              .konnektorService(konnektorService)
              .environment(Environment.PU)
              .build();

      assertNotNull(result);
    }
  }

  @Test
  void build_shouldPassThroughProxyAddressAndTelematikId() {
    var konnektorService = mock(KonnektorService.class);
    var trustStore = mock(KeyStore.class);
    var proxy = new InetSocketAddress("127.0.0.1", 8080);
    var telematikId = "abc-123";

    try (MockedStatic<Epa4AllClientFactory> factoryMock =
        Mockito.mockStatic(Epa4AllClientFactory.class)) {
      factoryMock
          .when(
              () ->
                  Epa4AllClientFactory.create(
                      eq(konnektorService),
                      eq(proxy),
                      eq(Environment.RU),
                      eq(trustStore),
                      eq(telematikId)))
          .thenReturn(mock(Epa4AllClientFactory.class));

      var result =
          Epa4AllClientFactoryBuilder.newBuilder()
              .konnektorService(konnektorService)
              .konnektorProxyAddress(proxy)
              .environment(Environment.RU)
              .trustStore(trustStore)
              .telematikId(telematikId)
              .build();

      assertNotNull(result);
    }
  }
}
