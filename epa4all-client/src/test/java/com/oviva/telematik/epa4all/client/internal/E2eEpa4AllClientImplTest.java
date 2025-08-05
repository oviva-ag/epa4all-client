package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.*;

import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.epa4all.client.Epa4AllClientFactoryBuilder;
import java.net.InetSocketAddress;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("e2e")
class E2eEpa4AllClientImplTest {

  static {
    Security.addProvider(new BouncyCastlePQCProvider());
    Security.addProvider(new BouncyCastleProvider());
  }

  private static final String KONNEKTOR_PROXY_HOST = "127.0.0.1";
  private static final int KONNEKTOR_PROXY_PORT = 3128;

  @Test
  void writeDocument() {

    System.setProperty("jdk.httpclient.HttpClient.log", "errors,requests,headers");

    try (var cf =
        Epa4AllClientFactoryBuilder.newBuilder()
            .konnektorProxyAddress(
                new InetSocketAddress(KONNEKTOR_PROXY_HOST, KONNEKTOR_PROXY_PORT))
            .konnektorService(TestKonnektors.riseKonnektor_RU())
            .environment(Environment.RU)
            .build()) {

      // Oviva RISE FdV
      final var insurantId = "X110684697";

      var client = cf.newClient();

      var document = ExportFixture.buildFhirDocument(client.authorInstitution(), insurantId);
      assertDoesNotThrow(() -> client.writeDocument(insurantId, document));
    }
  }
}
