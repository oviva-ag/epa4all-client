package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import com.oviva.telematik.vau.epa4all.client.authz.SignatureService;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthnClientAttesterTest {

  private SignatureService signatureService;
  private AuthnClientAttester attester;
  private X509Certificate mockCertificate;

  @BeforeEach
  void setUp() throws Exception {
    signatureService = mock(SignatureService.class);
    mockCertificate = mock(X509Certificate.class);
    attester = new AuthnClientAttester(signatureService);
    setupMockCertificate();
  }

  private void setupMockCertificate() throws CertificateEncodingException {
    when(signatureService.authCertificate()).thenReturn(mockCertificate);

    var mockCertBytes = "mock-certificate-data".getBytes();
    when(mockCertificate.getEncoded()).thenReturn(mockCertBytes);
    when(mockCertificate.getSubjectX500Principal())
        .thenReturn(new X500Principal("CN=Test Certificate"));
  }

  @Test
  void constructor_shouldCreateInstanceWithSignatureService() {
    var attester = new AuthnClientAttester(signatureService);

    assertNotNull(attester);
  }

  @Test
  void attestClient_shouldCreateValidSignedJWT() throws Exception {
    var nonce = "test-nonce-value";
    var mockSignature = "mock-signature".getBytes();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    var result = attester.attestClient(nonce);

    assertNotNull(result);
    assertTrue(result instanceof SignedJWT);

    var header = result.getHeader();
    assertEquals(JWSAlgorithm.ES256, header.getAlgorithm());
    assertEquals("JWT", header.getType().getType());
    assertNotNull(header.getX509CertChain());
    assertEquals(1, header.getX509CertChain().size());

    var claims = result.getJWTClaimsSet();
    assertEquals(nonce, claims.getClaim("nonce"));
    assertNotNull(claims.getIssueTime());
    assertNotNull(claims.getExpirationTime());

    verify(signatureService).authCertificate();
    verify(signatureService).authSign(any(byte[].class));
  }

  @Test
  void attestClient_shouldSetCorrectClaimsStructure() throws Exception {
    var nonce = "test-nonce-12345";
    var mockSignature = "mock-signature".getBytes();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    var result = attester.attestClient(nonce);

    var claims = result.getJWTClaimsSet();
    assertEquals(nonce, claims.getClaim("nonce"));
    assertNotNull(claims.getIssueTime());
    assertNotNull(claims.getExpirationTime());

    var iat = claims.getIssueTime().toInstant();
    var exp = claims.getExpirationTime().toInstant();
    var expectedExp = iat.plus(Duration.ofMinutes(20));
    assertEquals(expectedExp, exp);
  }

  @Test
  void attestClient_shouldUseES256Algorithm() throws Exception {
    var nonce = "test-nonce";
    var mockSignature = "mock-signature".getBytes();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    var result = attester.attestClient(nonce);

    var header = result.getHeader();
    assertEquals(JWSAlgorithm.ES256, header.getAlgorithm());
  }

  @Test
  void attestClient_shouldIncludeCertificateInHeader() throws Exception {
    var nonce = "test-nonce";
    var mockSignature = "mock-signature".getBytes();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    var result = attester.attestClient(nonce);

    var header = result.getHeader();
    assertNotNull(header.getX509CertChain());
    assertEquals(1, header.getX509CertChain().size());

    var certBase64 = header.getX509CertChain().get(0);
    assertNotNull(certBase64);
    assertTrue(certBase64.toString().length() > 0);
  }

  @Test
  void attestClient_shouldHandleEmptyNonce() throws Exception {
    var nonce = "";
    var mockSignature = "mock-signature".getBytes();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    var result = attester.attestClient(nonce);

    assertNotNull(result);
    var claims = result.getJWTClaimsSet();
    assertEquals("", claims.getClaim("nonce"));
  }

  @Test
  void attestClient_shouldHandleLongNonce() throws Exception {
    var nonce = "very-long-nonce-".repeat(50);
    var mockSignature = "mock-signature".getBytes();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    var result = attester.attestClient(nonce);

    assertNotNull(result);
    var claims = result.getJWTClaimsSet();
    assertEquals(nonce, claims.getClaim("nonce"));
  }

  @Test
  void attestClient_shouldThrowAuthorizationExceptionOnCertificateEncodingFailure()
      throws Exception {
    var nonce = "test-nonce";

    when(signatureService.authCertificate()).thenReturn(mockCertificate);
    when(mockCertificate.getEncoded())
        .thenThrow(new CertificateEncodingException("Encoding failed"));

    var exception = assertThrows(AuthorizationException.class, () -> attester.attestClient(nonce));

    assertEquals("failed client attestation - signing nonce", exception.getMessage());
    assertTrue(exception.getCause() instanceof CertificateEncodingException);
  }

  @Test
  void attestClient_shouldPropagateSigningExceptions() throws Exception {
    var nonce = "test-nonce";

    when(signatureService.authSign(any(byte[].class)))
        .thenThrow(new RuntimeException("Signing failed"));

    assertThrows(Exception.class, () -> attester.attestClient(nonce));
  }

  @Test
  void attestClient_shouldCreateValidJWTStructure() throws Exception {
    var nonce = "test-nonce-for-structure";
    var mockSignature = "mock-signature".getBytes();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    var result = attester.attestClient(nonce);

    var jwtString = result.serialize();
    var parts = jwtString.split("\\.");
    assertEquals(3, parts.length);
    assertTrue(parts[0].length() > 0);
    assertTrue(parts[1].length() > 0);
    assertTrue(parts[2].length() > 0);
  }

  @Test
  void attestClient_shouldSetCorrectExpirationTime() throws Exception {
    var nonce = "test-nonce";
    var mockSignature = "mock-signature".getBytes();
    var beforeCall = Instant.now();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    var result = attester.attestClient(nonce);

    var afterCall = Instant.now();
    var claims = result.getJWTClaimsSet();
    var iat = claims.getIssueTime().toInstant();
    var exp = claims.getExpirationTime().toInstant();

    assertTrue(iat.isAfter(beforeCall.minusSeconds(1)));
    assertTrue(iat.isBefore(afterCall.plusSeconds(1)));
    assertEquals(iat.plus(Duration.ofMinutes(20)), exp);
  }

  @Test
  void attestClient_shouldUseBrainpoolSigningInternally() throws Exception {
    var nonce = "test-nonce";
    var mockSignature = "mock-signature".getBytes();

    when(signatureService.authSign(any(byte[].class))).thenReturn(mockSignature);

    attester.attestClient(nonce);

    verify(signatureService).authSign(any(byte[].class));
  }
}
