package com.oviva.epa.client.konn;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KonnektorConnectionFactoryBuilderTest {

  private static final URI TEST_URI = URI.create("https://konnektor.example.com:443");
  private static final String TEST_KEYSTORE = "fixtures/test-keystore.p12";
  private static final String TEST_KEYSTORE_PASSWORD = "testpass";

  @TempDir Path tempDir;

  // --- build() ---

  @Test
  void build_withUri_returnsFactory() {
    var factory = KonnektorConnectionFactoryBuilder.newBuilder().konnektorUri(TEST_URI).build();

    assertNotNull(factory);
  }

  @Test
  void build_withoutUri_throwsIllegalArgumentException() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertThrows(IllegalArgumentException.class, builder::build);
  }

  // --- fluent API ---

  @Test
  void konnektorUri_returnsSameBuilder() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertSame(builder, builder.konnektorUri(TEST_URI));
  }

  @Test
  void konnektorServername_returnsSameBuilder() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertSame(builder, builder.konnektorServername("custom.konlan"));
  }

  @Test
  void tlsCiphersuites_returnsSameBuilder() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertSame(builder, builder.tlsCiphersuites(List.of("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")));
  }

  @Test
  void proxyServer_returnsSameBuilder() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertSame(builder, builder.proxyServer("proxy.local", 3128));
  }

  @Test
  void clientKeys_returnsSameBuilder() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertSame(builder, builder.clientKeys(List.of(mock(KeyManager.class))));
  }

  @Test
  void trustManagers_returnsSameBuilder() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertSame(builder, builder.trustManagers(List.of(mock(TrustManager.class))));
  }

  @Test
  void clientKeysFromP12_returnsSameBuilder() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertSame(
        builder, builder.clientKeysFromP12(resourcePath(TEST_KEYSTORE), TEST_KEYSTORE_PASSWORD));
  }

  // --- proxyServer ---

  @Test
  void proxyServer_nullAddress_buildSucceeds() {
    var factory =
        KonnektorConnectionFactoryBuilder.newBuilder()
            .konnektorUri(TEST_URI)
            .proxyServer(null, 3128)
            .build();

    assertNotNull(factory);
  }

  // --- clientKeysFromP12 ---

  @Test
  void clientKeysFromP12_validKeystore_buildSucceeds() {
    var factory =
        KonnektorConnectionFactoryBuilder.newBuilder()
            .konnektorUri(TEST_URI)
            .clientKeysFromP12(resourcePath(TEST_KEYSTORE), TEST_KEYSTORE_PASSWORD)
            .build();

    assertNotNull(factory);
  }

  @Test
  void clientKeysFromP12_fileNotFound_throwsIllegalArgumentException() {
    var missing = tempDir.resolve("missing.p12");
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertThrows(IllegalArgumentException.class, () -> builder.clientKeysFromP12(missing, "pw"));
  }

  @Test
  void clientKeysFromP12_invalidContent_throwsIllegalArgumentException() throws IOException {
    var bad = tempDir.resolve("bad.p12");
    Files.writeString(bad, "not-a-keystore");
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();

    assertThrows(IllegalArgumentException.class, () -> builder.clientKeysFromP12(bad, "pw"));
  }

  @Test
  void clientKeysFromP12_wrongPassword_throwsIllegalArgumentException() {
    var builder = KonnektorConnectionFactoryBuilder.newBuilder();
    var ks = resourcePath(TEST_KEYSTORE);

    assertThrows(
        IllegalArgumentException.class, () -> builder.clientKeysFromP12(ks, "wrong-password"));
  }

  // --- helpers ---

  private static Path resourcePath(String name) {
    var url = KonnektorConnectionFactoryBuilderTest.class.getClassLoader().getResource(name);
    assertNotNull(url, "test resource not found: " + name);
    return Path.of(url.getPath());
  }
}
