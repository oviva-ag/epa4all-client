package com.oviva.epa.client.konn.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import org.bouncycastle.asn1.x509.GeneralName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaticHostnameVerifierTest {

  private static final String KONNEKTOR_DNS_SAN = "konnektor.konlan";

  private StaticHostnameVerifier verifier;
  private SSLSession session;

  @BeforeEach
  void setUp() {
    verifier = new StaticHostnameVerifier(KONNEKTOR_DNS_SAN);
    session = mock(SSLSession.class);
  }

  @Test
  void verify_returnsFalse_whenPeerUnverified() throws SSLPeerUnverifiedException {
    when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("not verified"));

    assertFalse(verifier.verify("hostname", session));
  }

  @Test
  void verify_returnsFalse_whenCertHasNoSans() throws Exception {
    var cert = mock(X509Certificate.class);
    when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {cert});
    when(cert.getSubjectAlternativeNames()).thenReturn(null);

    assertFalse(verifier.verify("hostname", session));
  }

  @Test
  void verify_returnsFalse_whenDnsSanDoesNotMatch() throws Exception {
    var cert = mock(X509Certificate.class);
    when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {cert});
    when(cert.getSubjectAlternativeNames())
        .thenReturn(List.of(List.of(GeneralName.dNSName, "other.konlan")));

    assertFalse(verifier.verify("hostname", session));
  }

  @Test
  void verify_returnsTrue_whenDnsSanMatches() throws Exception {
    var cert = mock(X509Certificate.class);
    when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {cert});
    when(cert.getSubjectAlternativeNames())
        .thenReturn(List.of(List.of(GeneralName.dNSName, KONNEKTOR_DNS_SAN)));

    assertTrue(verifier.verify("hostname", session));
  }

  @Test
  void verify_returnsFalse_whenOnlyNonDnsSanPresent() throws Exception {
    var cert = mock(X509Certificate.class);
    when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {cert});
    when(cert.getSubjectAlternativeNames())
        .thenReturn(List.of(List.of(GeneralName.iPAddress, "10.0.0.1")));

    assertFalse(verifier.verify("hostname", session));
  }

  @Test
  void verify_returnsFalse_whenMatchingCertInIntermediate() throws Exception {
    var cert1 = mock(X509Certificate.class);
    var cert2 = mock(X509Certificate.class);
    when(session.getPeerCertificates())
        .thenReturn(new java.security.cert.Certificate[] {cert1, cert2});
    when(cert1.getSubjectAlternativeNames())
        .thenReturn(List.of(List.of(GeneralName.dNSName, "other.konlan")));
    when(cert2.getSubjectAlternativeNames())
        .thenReturn(List.of(List.of(GeneralName.dNSName, KONNEKTOR_DNS_SAN)));

    assertFalse(verifier.verify("hostname", session));
  }

  @Test
  void verify_returnsFalse_whenCertificateParsingFails() throws Exception {
    var cert = mock(X509Certificate.class);
    when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {cert});
    when(cert.getSubjectAlternativeNames()).thenThrow(new CertificateParsingException("bad cert"));

    assertFalse(verifier.verify("hostname", session));
  }
}
