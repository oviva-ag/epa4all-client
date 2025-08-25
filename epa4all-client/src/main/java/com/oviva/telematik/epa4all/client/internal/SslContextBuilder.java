package com.oviva.telematik.epa4all.client.internal;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/** Helper class for building SSL contexts with custom trust stores. */
final class SslContextBuilder {

  private SslContextBuilder() {}

  public static SSLContext buildSslContext(KeyStore trustStore) {
    return buildSslContextFromTrustManager(buildTrustManager(trustStore));
  }

  private static SSLContext buildSslContextFromTrustManager(TrustManager trustManager) {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
      sslContext.init(null, new TrustManager[] {trustManager}, null);
      return sslContext;
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new IllegalStateException("failed to initialise ssl context", e);
    }
  }

  private static TrustManager buildTrustManager(KeyStore trustStore) {
    try {
      var tmf = TrustManagerFactory.getInstance("PKIX");
      tmf.init(trustStore);
      return tmf.getTrustManagers()[0];
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("failed to initialize TrustManager from KeyStore", e);
    }
  }
}
