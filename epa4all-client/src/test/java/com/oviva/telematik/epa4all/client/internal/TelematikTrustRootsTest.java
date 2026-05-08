package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.security.Security;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TelematikTrustRootsTest {

  @BeforeAll
  static void setUp() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void loadRuTrustStore_returnsNonEmptyKeyStore() throws Exception {
    var ks = TelematikTrustRoots.loadRuTrustStore();

    assertNotNull(ks);
    assertTrue(ks.size() > 0, "RU trust store must contain at least one certificate");
  }

  @Test
  void loadPuTrustStore_returnsNonEmptyKeyStore() throws Exception {
    var ks = TelematikTrustRoots.loadPuTrustStore();

    assertNotNull(ks);
    assertTrue(ks.size() > 0, "PU trust store must contain at least one certificate");
  }

  @Test
  void loadRuTrustStore_containsX509Certificates() throws Exception {
    var ks = TelematikTrustRoots.loadRuTrustStore();

    var aliases = ks.aliases();
    while (aliases.hasMoreElements()) {
      var alias = aliases.nextElement();
      assertInstanceOf(X509Certificate.class, ks.getCertificate(alias));
    }
  }

  @Test
  void loadPuTrustStore_containsX509Certificates() throws Exception {
    var ks = TelematikTrustRoots.loadPuTrustStore();

    var aliases = ks.aliases();
    while (aliases.hasMoreElements()) {
      var alias = aliases.nextElement();
      assertInstanceOf(X509Certificate.class, ks.getCertificate(alias));
    }
  }

  @Test
  void createRuTrustManager_returnsNonNull() {
    var tm = TelematikTrustRoots.createRuTrustManager();

    assertNotNull(tm);
  }

  @Test
  void createPuTrustManager_returnsNonNull() {
    var tm = TelematikTrustRoots.createPuTrustManager();

    assertNotNull(tm);
  }

  @Test
  void createRuTrustManager_hasAcceptedIssuers() {
    var tm = (X509TrustManager) TelematikTrustRoots.createRuTrustManager();

    var issuers = tm.getAcceptedIssuers();
    assertNotNull(issuers);
    assertTrue(issuers.length > 0, "RU trust manager must have accepted issuers");
  }

  @Test
  void createPuTrustManager_hasAcceptedIssuers() {
    var tm = (X509TrustManager) TelematikTrustRoots.createPuTrustManager();

    var issuers = tm.getAcceptedIssuers();
    assertNotNull(issuers);
    assertTrue(issuers.length > 0, "PU trust manager must have accepted issuers");
  }
}
