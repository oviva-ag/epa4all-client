package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import com.oviva.telematik.vau.epa4all.client.authz.SignatureService;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolAlgorithms;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmcBSignerTest {

  private SignatureService signatureService;
  private SmcBSigner smcBSigner;

  @BeforeEach
  void setUp() {
    signatureService = mock(SignatureService.class);
    smcBSigner = new SmcBSigner(signatureService);
  }

  @Test
  void supportedJWSAlgorithms_shouldContainExpectedAlgorithms() {
    var supportedAlgorithms = smcBSigner.supportedJWSAlgorithms();

    assertEquals(2, supportedAlgorithms.size());
    assertTrue(supportedAlgorithms.contains(JWSAlgorithm.ES256));
    assertTrue(supportedAlgorithms.contains(BrainpoolAlgorithms.BS256R1));
  }

  @Test
  void sign_shouldSignWithES256Algorithm() throws JOSEException {
    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
    var signingInput = "test-signing-input".getBytes();
    var expectedSignature = "test-signature".getBytes();

    when(signatureService.authSign(signingInput)).thenReturn(expectedSignature);

    var result = smcBSigner.sign(header, signingInput);

    assertNotNull(result);
    assertEquals(Base64URL.encode(expectedSignature), result);
    verify(signatureService).authSign(signingInput);
  }

  @Test
  void sign_shouldSignWithBS256R1Algorithm() throws JOSEException {
    var header = new JWSHeader.Builder(BrainpoolAlgorithms.BS256R1).build();
    var signingInput = "test-signing-input".getBytes();
    var expectedSignature = "test-signature".getBytes();

    when(signatureService.authSign(signingInput)).thenReturn(expectedSignature);

    var result = smcBSigner.sign(header, signingInput);

    assertNotNull(result);
    assertEquals(Base64URL.encode(expectedSignature), result);
    verify(signatureService).authSign(signingInput);
  }

  @Test
  void sign_shouldThrowExceptionForUnsupportedAlgorithm() {
    var header = new JWSHeader.Builder(JWSAlgorithm.PS256).build();
    var signingInput = "test-signing-input".getBytes();

    var exception = assertThrows(JOSEException.class, () -> smcBSigner.sign(header, signingInput));

    assertTrue(exception.getMessage().contains("unsupported alg 'PS256'"));
    assertTrue(exception.getMessage().contains("supported:"));
    assertTrue(exception.getMessage().contains("ES256"));
    assertTrue(exception.getMessage().contains("BP256R1"));
  }

  @Test
  void sign_shouldThrowExceptionForRS256Algorithm() {
    var header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
    var signingInput = "test-signing-input".getBytes();

    var exception = assertThrows(JOSEException.class, () -> smcBSigner.sign(header, signingInput));

    assertTrue(exception.getMessage().contains("unsupported alg 'RS256'"));
    assertTrue(exception.getMessage().contains("supported:"));
    assertTrue(exception.getMessage().contains("ES256"));
    assertTrue(exception.getMessage().contains("BP256R1"));
  }

  @Test
  void sign_shouldHandleEmptySigningInput() throws JOSEException {
    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
    var signingInput = new byte[0];
    var expectedSignature = "empty-signature".getBytes();

    when(signatureService.authSign(signingInput)).thenReturn(expectedSignature);

    var result = smcBSigner.sign(header, signingInput);

    assertNotNull(result);
    assertEquals(Base64URL.encode(expectedSignature), result);
  }

  @Test
  void sign_shouldHandleLargeSigningInput() throws JOSEException {
    var header = new JWSHeader.Builder(BrainpoolAlgorithms.BS256R1).build();
    var signingInput = new byte[2048];
    var expectedSignature = "large-signature".getBytes();

    when(signatureService.authSign(signingInput)).thenReturn(expectedSignature);

    var result = smcBSigner.sign(header, signingInput);

    assertNotNull(result);
    assertEquals(Base64URL.encode(expectedSignature), result);
    verify(signatureService).authSign(signingInput);
  }

  @Test
  void getJCAContext_shouldThrowUnsupportedOperationException() {
    assertThrows(UnsupportedOperationException.class, () -> smcBSigner.getJCAContext());
  }

  @Test
  void constructor_shouldCreateInstanceWithNonNullSignatureService() {
    var signer = new SmcBSigner(signatureService);

    assertNotNull(signer);
  }

  @Test
  void sign_shouldPropagateSignatureServiceExceptions() {
    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
    var signingInput = "test-signing-input".getBytes();

    when(signatureService.authSign(any())).thenThrow(new RuntimeException("Service error"));

    assertThrows(RuntimeException.class, () -> smcBSigner.sign(header, signingInput));
  }
}
