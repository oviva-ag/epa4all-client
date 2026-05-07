package com.oviva.epa.client.konn.internal;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import org.bouncycastle.asn1.x509.GeneralName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StaticHostnameVerifier implements HostnameVerifier {

  private static final Logger logger = LoggerFactory.getLogger(StaticHostnameVerifier.class);

  private final String konnektorDnsSan;

  StaticHostnameVerifier(String konnektorDnsSan) {
    this.konnektorDnsSan = konnektorDnsSan;
  }

  @Override
  public boolean verify(String hostname, SSLSession session) {
    try {
      // throws if cert is otherwise invalid
      var peerCertificates = session.getPeerCertificates();

      // we allow the special SAN to be used as the hostname of the server
      return Arrays.stream(peerCertificates)
          .filter(X509Certificate.class::isInstance)
          .map(c -> (X509Certificate) c)
          .anyMatch(
              c -> {
                try {
                  var sans = c.getSubjectAlternativeNames();
                  if (sans == null) {
                    return false;
                  }
                  return sans.stream().anyMatch(this::matchesDnsSubjectAlternateName);
                } catch (CertificateParsingException e) {
                  logger.error(
                      "failed to parse peer certificate: %s".formatted(c.getSubjectX500Principal()),
                      e);
                  return false;
                }
              });
    } catch (SSLPeerUnverifiedException e) {
      logger.error("unverified peer", e);
      return false;
    }
  }

  private boolean matchesDnsSubjectAlternateName(List<?> sanTuple) {
    if (sanTuple == null || sanTuple.size() != 2) {
      return false;
    }

    if (!(sanTuple.get(0) instanceof Integer sanType && sanType == GeneralName.dNSName)) {
      return false;
    }

    return konnektorDnsSan.equals(sanTuple.get(1));
  }
}
