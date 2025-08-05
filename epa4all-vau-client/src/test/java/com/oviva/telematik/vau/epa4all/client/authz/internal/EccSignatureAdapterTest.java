package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.model.PinStatus;
import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EccSignatureAdapterTest {

  private KonnektorService konnektorService;
  private SmcbCard card;
  private EccSignatureAdapter eccSignatureAdapter;
  private X509Certificate mockCertificate;

  @BeforeEach
  void setUp() {
    konnektorService = mock(KonnektorService.class);
    card = mock(SmcbCard.class);
    mockCertificate = mock(X509Certificate.class);
    eccSignatureAdapter = new EccSignatureAdapter(konnektorService, card);
  }

  @Test
  void authCertificate_shouldReturnEccCertificateFromCard() {
    when(card.authEccCertificate()).thenReturn(mockCertificate);

    var result = eccSignatureAdapter.authCertificate();

    assertEquals(mockCertificate, result);
    verify(card).authEccCertificate();
  }

  @Test
  void authSign_shouldSignDataWhenPinIsVerified() {
    var cardHandle = "test-handle";
    var bytesToSign = "test data".getBytes();
    var expectedSignature = "signed-data".getBytes();

    when(card.handle()).thenReturn(cardHandle);
    when(konnektorService.verifySmcPin(cardHandle)).thenReturn(PinStatus.VERIFIED);
    when(konnektorService.authSignEcdsa(cardHandle, bytesToSign)).thenReturn(expectedSignature);

    var result = eccSignatureAdapter.authSign(bytesToSign);

    assertEquals(expectedSignature, result);
    verify(konnektorService).verifySmcPin(cardHandle);
    verify(konnektorService).authSignEcdsa(cardHandle, bytesToSign);
  }

  @Test
  void authSign_shouldThrowExceptionWhenPinNotVerified() {
    var cardHandle = "test-handle";
    var holderName = "Test Holder";
    var bytesToSign = "test data".getBytes();

    when(card.handle()).thenReturn(cardHandle);
    when(card.holderName()).thenReturn(holderName);
    when(konnektorService.verifySmcPin(cardHandle)).thenReturn(PinStatus.BLOCKED);

    var exception =
        assertThrows(Epa4AllClientException.class, () -> eccSignatureAdapter.authSign(bytesToSign));

    assertEquals("PIN not verified: Test Holder (test-handle)", exception.getMessage());
    verify(konnektorService).verifySmcPin(cardHandle);
  }

  @Test
  void authSign_shouldThrowExceptionWhenPinStatusIsTransport() {
    var cardHandle = "test-handle";
    var holderName = "Test Holder";
    var bytesToSign = "test data".getBytes();

    when(card.handle()).thenReturn(cardHandle);
    when(card.holderName()).thenReturn(holderName);
    when(konnektorService.verifySmcPin(cardHandle)).thenReturn(PinStatus.TRANSPORT_PIN);

    var exception =
        assertThrows(Epa4AllClientException.class, () -> eccSignatureAdapter.authSign(bytesToSign));

    assertEquals("PIN not verified: Test Holder (test-handle)", exception.getMessage());
    verify(konnektorService).verifySmcPin(cardHandle);
  }

  @Test
  void constructor_shouldCreateInstanceWithNonNullDependencies() {
    var adapter = new EccSignatureAdapter(konnektorService, card);

    assertNotNull(adapter);
  }

  @Test
  void authSign_shouldHandleEmptyBytesToSign() {
    var cardHandle = "test-handle";
    var bytesToSign = new byte[0];
    var expectedSignature = "empty-signature".getBytes();

    when(card.handle()).thenReturn(cardHandle);
    when(konnektorService.verifySmcPin(cardHandle)).thenReturn(PinStatus.VERIFIED);
    when(konnektorService.authSignEcdsa(cardHandle, bytesToSign)).thenReturn(expectedSignature);

    var result = eccSignatureAdapter.authSign(bytesToSign);

    assertEquals(expectedSignature, result);
  }

  @Test
  void authSign_shouldHandleLargeBytesToSign() {
    var cardHandle = "test-handle";
    var bytesToSign = new byte[1024];
    var expectedSignature = "large-signature".getBytes();

    when(card.handle()).thenReturn(cardHandle);
    when(konnektorService.verifySmcPin(cardHandle)).thenReturn(PinStatus.VERIFIED);
    when(konnektorService.authSignEcdsa(cardHandle, bytesToSign)).thenReturn(expectedSignature);

    var result = eccSignatureAdapter.authSign(bytesToSign);

    assertEquals(expectedSignature, result);
    verify(konnektorService).authSignEcdsa(cardHandle, bytesToSign);
  }
}
