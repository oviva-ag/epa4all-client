package com.oviva.telematik.vau.httpclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.oviva.telematik.vau.httpclient.internal.ConnectionFactory;
import com.oviva.telematik.vau.httpclient.internal.JavaHttpClient;
import com.oviva.telematik.vau.httpclient.internal.SignedPublicKeysTrustValidatorFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.Security;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("e2e")
class ConnectionFactoryTest {

  static {
    Security.addProvider(new BouncyCastlePQCProvider());
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void establishVauTunnel() {

    var vauUri = URI.create("https://e4a-rt.deine-epa.de/VAU");

    var tvf = mock(SignedPublicKeysTrustValidatorFactory.class);

    var cf =
        new ConnectionFactory(JavaHttpClient.from(HttpClient.newHttpClient()), "Test/0.0.1", tvf);
    var httpClient = cf.connect(vauUri);

    var res =
        httpClient.call(
            new HttpRequest(
                URI.create("/epa/authz/v1/getNonce"),
                "GET",
                List.of(
                    new HttpHeader("host", "e4a-rt15931.deine-epa.de"),
                    new HttpHeader("accept", "application/json"),
                    new HttpHeader("x-insurantid", "Z987654321"),
                    new HttpHeader("x-useragent", "Oviva/0.0.1")),
                null));
    assertEquals(200, res.status());
  }
}
