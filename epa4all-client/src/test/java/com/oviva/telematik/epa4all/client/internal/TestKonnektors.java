package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.fail;

import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.KonnektorServiceBuilder;
import com.oviva.epa.client.konn.KonnektorConnectionFactoryBuilder;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import org.apache.commons.io.IOUtils;

public class TestKonnektors {

  public static KonnektorService riseKonnektor_RU() {

    try {
      var proxyAdress = "127.0.0.1";
      var proxyPort = 3128;

      var tiKonnektorUri = "https://10.156.145.103:443";
      var keystoreFile = "keys/konnektor_keys.p12";
      var keystorePassword = "0000";
      var workplaceId = "a";
      var clientSystemId = "c";
      var mandantId = "m";
      var userId = "admin";

      // these are the TLS client credentials as received from the Konnektor provider (e.g. RISE)
      var keys = loadKeys(keystoreFile, keystorePassword);
      var uri = URI.create(tiKonnektorUri);

      var cf =
          KonnektorConnectionFactoryBuilder.newBuilder()
              .clientKeys(keys)
              .konnektorUri(uri)
              .proxyServer(proxyAdress, proxyPort)
              .trustAllServers() // currently we don't validate the server's certificate
              .build();

      var conn = cf.connect();

      return KonnektorServiceBuilder.newBuilder()
          .connection(conn)
          .workplaceId(workplaceId)
          .clientSystemId(clientSystemId)
          .mandantId(mandantId)
          .userId(userId)
          .build();
    } catch (Exception e) {
      fail("failed to create KonnektorService", e);
    }
    return null;
  }

  private static List<KeyManager> loadKeys(String keystoreFile, String password) throws Exception {
    var ks = loadKeyStore(keystoreFile, password);

    final KeyManagerFactory keyFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyFactory.init(ks, password.toCharArray());
    return Arrays.asList(keyFactory.getKeyManagers());
  }

  private static KeyStore loadKeyStore(String keystoreFile, String password)
      throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {

    var is =
        IOUtils.resourceToURL(keystoreFile, TestKonnektors.class.getClassLoader()).openStream();

    var keyStore = KeyStore.getInstance("PKCS12");

    keyStore.load(is, password.toCharArray());

    return keyStore;
  }
}
