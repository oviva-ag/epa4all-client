package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.epaapi.SoapClientFactory;
import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationService;
import com.oviva.telematik.vau.epa4all.client.info.InformationService;
import com.oviva.telematik.vau.proxy.VauProxy;
import java.util.List;
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
}
