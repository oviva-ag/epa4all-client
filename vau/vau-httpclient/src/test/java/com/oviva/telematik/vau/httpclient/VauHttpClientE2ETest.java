package com.oviva.telematik.vau.httpclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.oviva.telematik.vau.httpclient.internal.JavaHttpClient;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("e2e")
class VauHttpClientE2ETest {

  static {
    Security.addProvider(new BouncyCastlePQCProvider());
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void testStatus() {

    var vauUri = URI.create("https://epa-as-2.dev.epa4all.de/VAU"); // Bitmarck RU

    // connect VAU tunnel (unauthenticated)
    var client =
        VauClientFactoryBuilder.newBuilder()
            .xUserAgent("TEST/1.0.0-12")
            .outerClient(
                JavaHttpClient.from(
                    java.net.http.HttpClient.newBuilder()
                        .proxy(ProxySelector.of(new InetSocketAddress("localhost", 3128)))
                        .connectTimeout(Duration.ofSeconds(5))
                        .build()))
            .isPu(false)
            .withInsecureTrustValidator()
            .build();

    var conn = client.connect(vauUri);

    // get status
    var statusRes = conn.call(new HttpRequest(URI.create("/VAU-Status"), "GET", null, null));
    assertEquals(200, statusRes.status());

    // authenticate VAU tunnel
    // spec: https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/gemSpec_Krypt_V2.37.0/#7.3

    var nonceRes =
        conn.call(
            new HttpRequest(
                URI.create("/epa/authz/v1/getNonce"),
                "GET",
                List.of(
                    new HttpHeader("host", "e4a-rt15931.deine-epa.de"),
                    //                    new HttpClient.Header("x-insurantid", "Z987654321"),
                    new HttpHeader("accept", "application/json")),
                null));

    assertEquals(200, nonceRes.status());
    assertNotNull(nonceRes.body());
  }
}
