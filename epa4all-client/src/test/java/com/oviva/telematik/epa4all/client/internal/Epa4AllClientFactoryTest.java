package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.epaapi.SoapClientFactory;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationService;
import com.oviva.telematik.vau.epa4all.client.info.InformationService;
import com.oviva.telematik.vau.proxy.VauProxy;
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
  @Mock private SmcbCard mockCard;

  @InjectMocks private Epa4AllClientFactory factory;

  @BeforeEach
  void setUp() {
    // Setup mock card to avoid NPE in Epa4AllClientImpl constructor - using lenient to avoid
    // unnecessary stubbing errors
    lenient().when(mockCard.telematikId()).thenReturn("test-telematik-id");
    lenient().when(mockCard.holderName()).thenReturn("Test Holder");
  }

  // Constructor tests - testing the primary factory method

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
}
