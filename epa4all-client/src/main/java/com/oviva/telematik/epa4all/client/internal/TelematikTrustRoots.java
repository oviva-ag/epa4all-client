package com.oviva.telematik.epa4all.client.internal;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class TelematikTrustRoots {
  private TelematikTrustRoots() {}

  // this is nothing secret - its just that Java and PKCS12 don't work well if there is no password
  // set at all
  private static final String TRUSTSTORE_PW = "1234";

  public static TrustManager createPuTrustManager() {
    return convertToTrustManager(loadPuTrustStore());
  }

  public static TrustManager createRuTrustManager() {
    return convertToTrustManager(loadRuTrustStore());
  }

  public static KeyStore loadRuTrustStore() {
    return loadP12KeyStore("/truststore-test.p12");
  }

  public static KeyStore loadPuTrustStore() {
    return loadP12KeyStore("/truststore-pu.p12");
  }

  private static KeyStore loadP12KeyStore(String path) {
    try {
      var ks = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
      ks.load(TelematikTrustRoots.class.getResourceAsStream(path), TRUSTSTORE_PW.toCharArray());
      if (ks.size() == 0) {
        throw new IllegalStateException("keystore %s is empty".formatted(path));
      }
      return ks;
    } catch (CertificateException
        | KeyStoreException
        | IOException
        | NoSuchAlgorithmException
        | NoSuchProviderException e) {
      throw new IllegalStateException("failed to load keystore: " + path, e);
    }
  }

  private static TrustManager convertToTrustManager(KeyStore trustStore) {
    try {
      var tmf = TrustManagerFactory.getInstance("PKIX");
      tmf.init(trustStore);
      return tmf.getTrustManagers()[0];
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("failed to initialize TrustManager from KeyStore", e);
    }
  }
}
