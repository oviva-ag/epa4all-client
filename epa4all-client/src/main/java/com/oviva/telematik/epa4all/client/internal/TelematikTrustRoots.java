package com.oviva.telematik.epa4all.client.internal;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class TelematikTrustRoots {

  private static final String TRUSTSTORE_PW = "1234";

  public static KeyStore loadRuTruststore() {
    return loadP12KeyStore("/truststore-test.p12");
  }

  public static KeyStore loadPuTruststore() {
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
}
