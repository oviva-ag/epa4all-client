package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.epaapi.SoapClientFactory;
import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationService;
import com.oviva.telematik.vau.epa4all.client.info.InformationService;
import com.oviva.telematik.vau.proxy.VauProxy;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Epa4AllClientFactoryTest {

  @Mock private VauProxy mockVauProxy;
  @Mock private SoapClientFactory mockSoapClientFactory;
  @Mock private AuthorizationService mockAuthorizationService;
  @Mock private InformationService mockInformationService;
  @Mock private SmcbCard mockCard1;
  @Mock private SmcbCard mockCard2;
  @Mock private KonnektorService konnektorService;

  @InjectMocks private Epa4AllClientFactory factory;

  @BeforeEach
  void setUp() {
    // Setup mock card to avoid NPE in Epa4AllClientImpl constructor - using lenient to avoid
    // unnecessary stubbing errors
    lenient().when(mockCard1.telematikId()).thenReturn("test-telematik-id-1");
    lenient().when(mockCard1.holderName()).thenReturn("Test Holder");

    lenient().when(mockCard2.telematikId()).thenReturn("test-telematik-id-2");
    lenient().when(mockCard2.holderName()).thenReturn("Test Holder");
  }

  @Test
  void newClient_shouldReturnEpa4AllClientImpl() {
    // When
    var client = factory.newClient();

    // Then
    assertNotNull(client);
    assertInstanceOf(Epa4AllClientImpl.class, client);
  }

  @Test
  void newClient_shouldCreateNewInstanceEachTime() {
    // When
    var client1 = factory.newClient();
    var client2 = factory.newClient();

    // Then
    assertNotNull(client1);
    assertNotNull(client2);
    assertNotSame(client1, client2);
  }

  @Test
  void close_shouldStopVauProxy() {
    // When
    factory.close();

    // Then
    verify(mockVauProxy).stop();
  }

  @Test
  void findSmcBCard_shouldThrowWhenNoCardsFound() {
    when(konnektorService.listSmcbCards()).thenReturn(List.of());

    var exception =
        assertThrows(
            Epa4AllClientException.class,
            () -> Epa4AllClientFactory.findSmcBCard(konnektorService, null));
    assertEquals("no SMC-B cards found", exception.getMessage());
  }

  @Test
  void findSmcBCard_shouldSelectFirstWhenTelematikIdIsNull() {
    var cards = List.of(mockCard1, mockCard2);
    when(konnektorService.listSmcbCards()).thenReturn(cards);

    var result = Epa4AllClientFactory.findSmcBCard(konnektorService, null);
    assertEquals(cards.getFirst(), result);
  }

  @Test
  void findSmcBCard_shouldSelectCorrectCardForTelematikId() {
    var cards = List.of(mockCard1, mockCard2);

    when(konnektorService.listSmcbCards()).thenReturn(cards);

    var result = Epa4AllClientFactory.findSmcBCard(konnektorService, "test-telematik-id-1");
    assertEquals(mockCard1, result);
  }

  @Test
  void findSmcBCard_shouldSelectFirstWhenMultipleMatchesForTelematikId() {
    var cards = List.of(mockCard1, mockCard1);

    when(konnektorService.listSmcbCards()).thenReturn(cards);

    var result = Epa4AllClientFactory.findSmcBCard(konnektorService, "test-telematik-id-1");
    assertEquals(mockCard1, result);
  }

  @Test
  void findSmcBCard_shouldThrowWhenTelematikIdNotFound() {
    var cards = List.of(mockCard1, mockCard2);
    when(konnektorService.listSmcbCards()).thenReturn(cards);

    var exception =
        assertThrows(
            Epa4AllClientException.class,
            () ->
                Epa4AllClientFactory.findSmcBCard(konnektorService, "nonexistintent-telematik-id"));
    assertTrue(
        exception
            .getMessage()
            .contains("no SMC-B card found for telematikId [ nonexistintent-telematik-id ]"));
  }

  @Test
  void findSmcBCard_singleCard_nullTelematikId_returnsCard() {
    when(konnektorService.listSmcbCards()).thenReturn(List.of(mockCard1));

    var result = Epa4AllClientFactory.findSmcBCard(konnektorService, null);

    assertEquals(mockCard1, result);
  }

  @Test
  void findSmcBCard_shouldSelectSecondCardByTelematikId() {
    when(konnektorService.listSmcbCards()).thenReturn(List.of(mockCard1, mockCard2));

    var result = Epa4AllClientFactory.findSmcBCard(konnektorService, "test-telematik-id-2");

    assertEquals(mockCard2, result);
  }

  @Test
  void findSmcBCard_notFound_errorMessageContainsAvailableCardDetails() {
    when(konnektorService.listSmcbCards()).thenReturn(List.of(mockCard1, mockCard2));

    var exception =
        assertThrows(
            Epa4AllClientException.class,
            () -> Epa4AllClientFactory.findSmcBCard(konnektorService, "unknown-id"));

    var msg = exception.getMessage();
    assertTrue(msg.contains("'test-telematik-id-1' (Test Holder)"));
    assertTrue(msg.contains("'test-telematik-id-2' (Test Holder)"));
  }

  @Test
  void create_noCardsFound_throwsEpa4AllClientException() throws NoSuchAlgorithmException {
    var proxyAddr = new InetSocketAddress("127.0.0.1", 9999);

    var ks = mock(KonnektorService.class);
    when(ks.listSmcbCards()).thenReturn(List.of());

    var trustStore = mock(KeyStore.class);
    var proxyListenAddr = new InetSocketAddress("127.0.0.1", 18080);

    try (var sslMock = mockStatic(SslContextBuilder.class);
        var ignored =
            mockConstruction(
                VauProxy.class,
                (proxy, ctx) ->
                    when(proxy.start()).thenReturn(new VauProxy.ServerInfo(proxyListenAddr)))) {
      sslMock
          .when(() -> SslContextBuilder.buildSslContext(any()))
          .thenReturn(SSLContext.getDefault());

      // When & Then
      assertThrows(
          Epa4AllClientException.class,
          () -> Epa4AllClientFactory.create(ks, proxyAddr, Environment.RU, trustStore, null));
    }
  }

  @Test
  void create_withSingleCard_returnsFactory() throws NoSuchAlgorithmException {
    var proxyAddr = new InetSocketAddress("127.0.0.1", 9999);
    var ks = mock(KonnektorService.class);
    var card = mock(SmcbCard.class);
    when(ks.listSmcbCards()).thenReturn(List.of(card));
    when(card.telematikId()).thenReturn("test-id");
    var trustStore = mock(KeyStore.class);
    var proxyListenAddr = new InetSocketAddress("127.0.0.1", 18080);

    try (var sslMock = mockStatic(SslContextBuilder.class);
        var ignored =
            mockConstruction(
                VauProxy.class,
                (proxy, ctx) ->
                    when(proxy.start()).thenReturn(new VauProxy.ServerInfo(proxyListenAddr)));
        var ignored2 = mockConstruction(SoapClientFactory.class)) {
      sslMock
          .when(() -> SslContextBuilder.buildSslContext(any()))
          .thenReturn(SSLContext.getDefault());

      // When
      var result =
          Epa4AllClientFactory.create(ks, proxyAddr, Environment.RU, trustStore, "test-id");

      // Then
      assertNotNull(result);
      assertInstanceOf(Epa4AllClientFactory.class, result);
    }
  }

  @Test
  void create_withTelematikId_selectsMatchingCard() throws NoSuchAlgorithmException {
    var proxyAddr = new InetSocketAddress("127.0.0.1", 9999);
    var ks = mock(KonnektorService.class);
    var card1 = mock(SmcbCard.class);
    var card2 = mock(SmcbCard.class);
    when(card1.telematikId()).thenReturn("id-1");
    when(card2.telematikId()).thenReturn("id-2");
    when(ks.listSmcbCards()).thenReturn(List.of(card1, card2));
    var trustStore = mock(KeyStore.class);
    var proxyListenAddr = new InetSocketAddress("127.0.0.1", 18080);

    try (var sslMock = mockStatic(SslContextBuilder.class);
        var ignored =
            mockConstruction(
                VauProxy.class,
                (proxy, ctx) ->
                    when(proxy.start()).thenReturn(new VauProxy.ServerInfo(proxyListenAddr)));
        var ignored2 = mockConstruction(SoapClientFactory.class)) {
      sslMock
          .when(() -> SslContextBuilder.buildSslContext(any()))
          .thenReturn(SSLContext.getDefault());

      // When
      var result = Epa4AllClientFactory.create(ks, proxyAddr, Environment.RU, trustStore, "id-2");

      // Then
      assertNotNull(result);
    }
  }
}
