package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.oviva.telematik.vau.epa4all.client.authz.SignatureService;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthnChallengeResponderTest {

  @Mock private SignatureService signatureService;

  @Mock private OidcClient oidcClient;

  @Mock private X509Certificate certificate;

  private AuthnChallengeResponder responder;

  @BeforeAll
  static void setupSecurity() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    responder = new AuthnChallengeResponder(signatureService, oidcClient);
    setupMockCertificate();
  }

  private void setupMockCertificate() throws CertificateEncodingException {
    when(signatureService.authCertificate()).thenReturn(certificate);

    var mockCertBytes = "mock-certificate-data".getBytes();
    when(certificate.getEncoded()).thenReturn(mockCertBytes);
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=Test Certificate"));
  }

  @Test
  void constructor_shouldCreateInstanceWithDependencies() {
    var responder = new AuthnChallengeResponder(signatureService, oidcClient);

    assertNotNull(responder);
  }
}
