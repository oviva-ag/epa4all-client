package com.oviva.telematik.vau.httpclient;

import com.oviva.telematik.vau.httpclient.internal.ConnectionFactory;
import com.oviva.telematik.vau.httpclient.internal.JavaHttpClient;
import com.oviva.telematik.vau.httpclient.internal.SignedPublicKeysTrustValidatorFactoryImpl;
import com.oviva.telematik.vau.httpclient.internal.cert.TrustValidator;
import java.time.Duration;

public class VauClientFactoryBuilder {

  public static VauClientFactoryBuilder newBuilder() {
    return new VauClientFactoryBuilder();
  }

  private boolean isPu = true;

  private TrustValidator trustValidator = null;

  private String xUserAgent;

  private HttpClient outerClient =
      JavaHttpClient.from(
          java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());

  private VauClientFactoryBuilder() {}

  public VauClientFactoryBuilder outerClient(HttpClient outerClient) {
    this.outerClient = outerClient;
    return this;
  }

  public VauClientFactoryBuilder isPu(boolean isPu) {
    this.isPu = isPu;
    return this;
  }

  public VauClientFactoryBuilder trustValidator(TrustValidator trustValidator) {
    this.trustValidator = trustValidator;
    return this;
  }

  public VauClientFactoryBuilder xUserAgent(String xUserAgent) {
    this.xUserAgent = xUserAgent;
    return this;
  }

  public VauClientFactoryBuilder withInsecureTrustValidator() {
    this.trustValidator = (a, b, c, d) -> new TrustValidator.ValidationResult(true, null, null);
    return this;
  }

  /**
   * Returns an HttpClient that uses the VAU transport as documented in <a
   * href="https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/gemSpec_Krypt_V2.37.0/#7">gemSpec_Krypt</a>.
   * The {@link HttpClient} directly sends requests in the HTTP protocol through the VAU tunnel.
   */
  public VauClientFactory build() {

    if (outerClient == null) {
      throw new IllegalArgumentException("outer client missing");
    }

    if (trustValidator == null) {
      throw new IllegalArgumentException("trust validator missing");
    }

    if (xUserAgent == null) {
      xUserAgent = "TEST1234567890AB/2.1.12-45";
    }

    var clientFactory =
        new SignedPublicKeysTrustValidatorFactoryImpl(isPu, outerClient, trustValidator);
    return new ConnectionFactory(outerClient, xUserAgent, clientFactory);
  }
}
