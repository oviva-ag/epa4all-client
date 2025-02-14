package com.oviva.telematik.vau.httpclient.internal.cert;

import java.security.cert.X509Certificate;
import java.util.List;

public interface TrustValidator {
  /**
   * Implements A_24624-01
   *
   * <p>https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/latest/#A_24624-01
   *
   * <p>The requirement for the client to validate the certificate chain
   *
   * @param certificate the certificate to validate
   * @param issuerCa the CA that issued the certificate (i.e. first part of the chain)
   * @param certificateChain the chain of certificates leading to a TI root certificate. Root
   *     certificates can be obtained via the <a
   *     href="https://gemspec.gematik.de/docs/gemKPT/gemKPT_PKI_TIP/latest/#2.3.2">TSL</a>
   * @return whether the certificate can be trusted or not
   */
  void validate(
      X509Certificate certificate,
      X509Certificate issuerCa,
      List<X509Certificate> certificateChain,
      byte[] ocspResponseDer)
      throws CertificateValidationException;
}
