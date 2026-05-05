package com.oviva.telematik.vau.httpclient.internal;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.internal.cert.TrustValidator;
import com.oviva.telematik.vau.httpclient.internal.cert.VauCertificateClient;
import de.gematik.vau.lib.VauClientStateMachine;
import java.net.URI;

public class SignedPublicKeysTrustValidatorFactory {

  private final boolean isPu;
  private final HttpClient outerClient;
  private final TrustValidator trustValidator;

  public SignedPublicKeysTrustValidatorFactory(
      boolean isPu, HttpClient outerClient, TrustValidator trustValidator) {
    this.isPu = isPu;
    this.outerClient = outerClient;
    this.trustValidator = trustValidator;
  }

  public VauClientStateMachine create(URI vauUri) {
    var certClient = new VauCertificateClient(outerClient, trustValidator);
    return new VauClientStateMachine(
        isPu, new SignedPublicKeysTrustValidatorImpl(certClient, vauUri));
  }
}
