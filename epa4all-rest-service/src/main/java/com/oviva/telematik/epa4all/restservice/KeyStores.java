package com.oviva.telematik.epa4all.restservice;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class KeyStores {

  private KeyStores() {}

  @NonNull
  public static List<KeyManager> loadKeys(@NonNull Path keystoreFile, @Nullable String password) {
    try {
      var ks = loadPkcs12KeyStore(keystoreFile, password);

      var keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyFactory.init(ks, password != null ? password.toCharArray() : null);
      return Arrays.asList(keyFactory.getKeyManagers());
    } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
      throw new IllegalArgumentException(
          "failed to load keys from '%s', absolute path '%s'"
              .formatted(keystoreFile, keystoreFile.toAbsolutePath()),
          e);
    }
  }

  @Nullable
  public static TrustManager convertToTrustManager(@Nullable KeyStore trustStore) {
    try {
      if (trustStore == null) {
        return null;
      }

      var tmf = TrustManagerFactory.getInstance("PKIX");
      tmf.init(trustStore);
      return tmf.getTrustManagers()[0];
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("failed to initialize TrustManager from KeyStore", e);
    }
  }

  @NonNull
  public static KeyStore loadPkcs12KeyStore(@NonNull Path keystoreFile, @Nullable String password) {

    try (var is = Files.newInputStream(keystoreFile)) {

      var keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(is, password != null ? password.toCharArray() : null);

      return keyStore;
    } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("failed to load keystore: " + keystoreFile, e);
    }
  }
}
