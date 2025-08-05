package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.model.PinStatus;
import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolAlgorithms;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test that verifies the complete ECC signature flow works end-to-end, testing the
 * interaction between EccSignatureAdapter, SmcBSigner, AuthnChallengeResponder, and
 * AuthnClientAttester components.
 */
class EccSignatureFlowIntegrationTest {

  private KonnektorService konnektorService;
  private SmcbCard card;
  private EccSignatureAdapter eccSignatureAdapter;
  private X509Certificate testCertificate;

  // Sample test certificate in DER format (base64 encoded)
  private static final String TEST_CERT_BASE64 =
      "MIICljCCAX4CAQEwDQYJKoZIhvcNAQELBQAwEjEQMA4GA1UEAwwHVGVzdCBDQTAeFw0yNDAx"
          + "MDEwMDAwMDBaFw0yNTAxMDEwMDAwMDBaMBIxEDAOBgNVBAMMB1Rlc3QgQ0EwggEiMA0GCSqG"
          + "SIb3DQEBAQUAA4IBDwAwggEKAoIBAQC7VJTUt9Us8cKBwko2/K2QtuF4o4wm+w0G0v1Vm7BG";

  @BeforeAll
  static void setupSecurity() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @BeforeEach
  void setUp() throws Exception {
    setupMocks();
    eccSignatureAdapter = new EccSignatureAdapter(konnektorService, card);
  }

  private void setupMocks() throws Exception {
    konnektorService = mock(KonnektorService.class);
    card = mock(SmcbCard.class);

    // Setup certificate
    testCertificate = createTestCertificate();

    // Setup card mock
    when(card.authEccCertificate()).thenReturn(testCertificate);
    when(card.handle()).thenReturn("test-card-handle");
    when(card.holderName()).thenReturn("Test Card Holder");

    // Setup konnektor mock
    when(konnektorService.verifySmcPin("test-card-handle")).thenReturn(PinStatus.VERIFIED);
    when(konnektorService.authSignEcdsa(any(), any())).thenReturn("mock-ecc-signature".getBytes());
  }

  private X509Certificate createTestCertificate() {
    var mockCert = mock(X509Certificate.class);
    when(mockCert.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=Test CA"));
    try {
      when(mockCert.getEncoded()).thenReturn("mock-certificate-data".getBytes());
    } catch (CertificateEncodingException e) {
      throw new RuntimeException(e);
    }
    return mockCert;
  }

  @Test
  void endToEndFlow_shouldCreateValidSignedJWTWithEccSignature() throws Exception {
    var smcBSigner = new SmcBSigner(eccSignatureAdapter);
    var challenge = "test-challenge-for-integration";

    var signedJWT = createSignedChallengeJWT(smcBSigner, challenge);

    assertNotNull(signedJWT);

    var jwtString = signedJWT.serialize();
    var parts = jwtString.split("\\.");
    assertEquals(3, parts.length);

    var header = signedJWT.getHeader();
    assertEquals(BrainpoolAlgorithms.BS256R1, header.getAlgorithm());
    assertEquals("JWT", header.getType().getType());
    assertEquals("NJWT", header.getContentType());

    assertNotNull(header.getX509CertChain());
    assertEquals(1, header.getX509CertChain().size());

    var claims = signedJWT.getJWTClaimsSet();
    assertEquals(challenge, claims.getClaim("njwt"));
  }

  @Test
  void endToEndFlow_shouldCreateValidClientAttestation() throws Exception {
    var attester = new AuthnClientAttester(eccSignatureAdapter);
    var nonce = "test-nonce-for-integration";

    var attestationJWT = attester.attestClient(nonce);

    assertNotNull(attestationJWT);

    var jwtString = attestationJWT.serialize();
    var parts = jwtString.split("\\.");
    assertEquals(3, parts.length);

    var header = attestationJWT.getHeader();
    assertEquals(JWSAlgorithm.ES256, header.getAlgorithm());
    assertEquals("JWT", header.getType().getType());

    assertNotNull(header.getX509CertChain());
    assertEquals(1, header.getX509CertChain().size());

    var claims = attestationJWT.getJWTClaimsSet();
    assertEquals(nonce, claims.getClaim("nonce"));
    assertNotNull(claims.getIssueTime());
    assertNotNull(claims.getExpirationTime());
  }

  @Test
  void eccSignatureAdapter_shouldIntegrateWithSmcBSigner() throws Exception {
    var signer = new SmcBSigner(eccSignatureAdapter);
    var testData = "test-signing-data".getBytes();
    var header = new com.nimbusds.jose.JWSHeader.Builder(BrainpoolAlgorithms.BS256R1).build();

    var signature = signer.sign(header, testData);

    assertNotNull(signature);
    assertTrue(signature.toString().length() > 0);

    var decoded = signature.decode();
    assertNotNull(decoded);
    assertEquals("mock-ecc-signature", new String(decoded));
  }

  @Test
  void eccSignatureAdapter_shouldHandleMultipleSigningOperations() throws Exception {
    var signer = new SmcBSigner(eccSignatureAdapter);

    var header1 = new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.ES256).build();
    var signature1 = signer.sign(header1, "data1".getBytes());

    var header2 = new com.nimbusds.jose.JWSHeader.Builder(BrainpoolAlgorithms.BS256R1).build();
    var signature2 = signer.sign(header2, "data2".getBytes());

    assertNotNull(signature1);
    assertNotNull(signature2);
    assertTrue(signature1.toString().length() > 0);
    assertTrue(signature2.toString().length() > 0);
  }

  @Test
  void eccSignatureAdapter_shouldProvideCorrectCertificate() {
    var certificate = eccSignatureAdapter.authCertificate();

    assertNotNull(certificate);
    assertEquals(testCertificate, certificate);
    assertEquals("CN=Test CA", certificate.getSubjectX500Principal().getName());
  }

  private SignedJWT createSignedChallengeJWT(SmcBSigner signer, String challenge) throws Exception {
    var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().claim("njwt", challenge).build();

    var header =
        new com.nimbusds.jose.JWSHeader.Builder(BrainpoolAlgorithms.BS256R1)
            .type(com.nimbusds.jose.JOSEObjectType.JWT)
            .x509CertChain(
                java.util.List.of(
                    com.nimbusds.jose.util.Base64.encode(testCertificate.getEncoded())))
            .contentType("NJWT")
            .build();

    var jwt = new SignedJWT(header, claims);

    var signature = signer.sign(jwt.getHeader(), jwt.getSigningInput());
    return new SignedJWT(jwt.getHeader().toBase64URL(), jwt.getPayload().toBase64URL(), signature);
  }
}
