package com.oviva.telematik.epa4all.restservice;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeyStoresTest {

  private static final String TEST_KEYSTORE = "fixtures/test-keystore.p12";
  private static final String TEST_KEYSTORE_PASSWORD = "testpass";
  private static final String TRUSTSTORE = "fixtures/truststore-tiaas-ru.p12";
  private static final String TRUSTSTORE_PASSWORD = "changeit";

  @TempDir Path tempDir;

  @Test
  void loadPkcs12KeyStore_withPassword_succeeds() {
    var file = createEmptyPkcs12(tempDir.resolve("ks.p12"), "secret");

    var result = KeyStores.loadPkcs12KeyStore(file, "secret");

    assertNotNull(result);
  }

  @Test
  void loadPkcs12KeyStore_withNullPassword_succeeds() {
    var file = createEmptyPkcs12(tempDir.resolve("ks-no-pw.p12"), null);

    var result = KeyStores.loadPkcs12KeyStore(file, null);

    assertNotNull(result);
  }

  @Test
  void loadPkcs12KeyStore_realKeystore_loadsEntries() {
    var file = resourcePath(TEST_KEYSTORE);

    var result = KeyStores.loadPkcs12KeyStore(file, TEST_KEYSTORE_PASSWORD);

    assertNotNull(result);
  }

  @Test
  void loadPkcs12KeyStore_fileNotFound_throwsIllegalStateException() {
    var missing = tempDir.resolve("missing.p12");

    assertThrows(IllegalStateException.class, () -> KeyStores.loadPkcs12KeyStore(missing, "pw"));
  }

  @Test
  void loadPkcs12KeyStore_invalidContent_throwsIllegalStateException() throws IOException {
    var bad = tempDir.resolve("bad.p12");
    Files.writeString(bad, "not-a-keystore");

    assertThrows(IllegalStateException.class, () -> KeyStores.loadPkcs12KeyStore(bad, "pw"));
  }

  @Test
  void loadKeys_emptyKeystoreWithPassword_returnsKeyManagers() {
    var file = createEmptyPkcs12(tempDir.resolve("empty.p12"), "pw");

    var result = KeyStores.loadKeys(file, "pw");

    assertNotNull(result);
  }

  @Test
  void loadKeys_withNullPassword_returnsKeyManagers() {
    var file = createEmptyPkcs12(tempDir.resolve("empty-no-pw.p12"), null);

    var result = KeyStores.loadKeys(file, null);

    assertNotNull(result);
  }

  @Test
  void loadKeys_realKeystoreWithKeys_returnsNonEmptyList() {
    var file = resourcePath(TEST_KEYSTORE);

    var result = KeyStores.loadKeys(file, TEST_KEYSTORE_PASSWORD);

    assertFalse(result.isEmpty());
  }

  @Test
  void loadKeys_fileNotFound_throwsIllegalStateException() {
    var missing = tempDir.resolve("missing-keys.p12");

    assertThrows(IllegalStateException.class, () -> KeyStores.loadKeys(missing, "pw"));
  }

  @Test
  void convertToTrustManager_nullInput_returnsNull() {
    var result = KeyStores.convertToTrustManager(null);

    assertNull(result);
  }

  @Test
  void convertToTrustManager_emptyKeyStore_returnsTrustManager() throws Exception {
    var ks = KeyStore.getInstance("PKCS12");
    ks.load(null, null);

    var result = KeyStores.convertToTrustManager(ks);

    assertInstanceOf(TrustManager.class, result);
  }

  @Test
  void convertToTrustManager_loadedTrustStore_returnsTrustManager() {
    var ks = KeyStores.loadPkcs12KeyStore(resourcePath(TRUSTSTORE), TRUSTSTORE_PASSWORD);

    var result = KeyStores.convertToTrustManager(ks);

    assertNotNull(result);
  }

  private static Path createEmptyPkcs12(Path file, String password) {
    try {
      Files.createDirectories(file.getParent());
      var ks = KeyStore.getInstance("PKCS12");
      ks.load(null, password != null ? password.toCharArray() : null);
      try (var os = Files.newOutputStream(file)) {
        ks.store(os, password != null ? password.toCharArray() : null);
      }
      return file;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Path resourcePath(String name) {
    var url = KeyStoresTest.class.getClassLoader().getResource(name);
    assertNotNull(url, "test resource not found: " + name);
    return Path.of(url.getPath());
  }
}
