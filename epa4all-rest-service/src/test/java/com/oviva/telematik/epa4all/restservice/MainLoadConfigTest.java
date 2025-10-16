package com.oviva.telematik.epa4all.restservice;

import static com.oviva.telematik.epa4all.restservice.Main.CONFIG_PREFIX;
import static org.junit.jupiter.api.Assertions.*;

import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.epa4all.restservice.cfg.ConfigProvider;
import com.oviva.telematik.epa4all.restservice.cfg.EnvConfigProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainLoadConfigTest {

  @TempDir Path tempDir;

  @Test
  void loadConfig_usesDefaultsWhenOptionalValuesMissing() throws Exception {

    var keystore = createEmptyPkcs12(tempDir.resolve("keys-default.p12"), "0000");

    var cfg =
        mapProvider(
            Map.of(
                "konnektor.uri",
                "https://example.org:443",
                "credentials.path",
                keystore.toString()));

    var main = new Main(cfg);

    var result = invokeLoadConfig(main, cfg);

    assertEquals(URI.create("https://example.org:443"), result.konnektorUri());
    assertNull(result.proxyAddress());
    assertEquals(3128, result.proxyPort());
    assertNotNull(result.clientKeys());
    assertEquals("a", result.workplaceId());
    assertEquals("m", result.mandantId());
    assertEquals("c", result.clientSystemId());
    assertEquals("admin", result.userId());
    assertEquals("0.0.0.0", result.address());
    assertEquals(8080, result.port());
    assertEquals(Environment.PU, result.environment());
    assertNull(result.telematikId());
  }

  @Test
  void loadConfig_readsFromEnvironmentVariables() throws Exception {
    var keystore = createEmptyPkcs12(tempDir.resolve("keys-env.p12"), "pw");

    var env = new HashMap<String, String>();
    env.put("EPA4ALL_KONNEKTOR_URI", "https://env.konn:1234");
    env.put("EPA4ALL_CREDENTIALS_PATH", keystore.toString());
    env.put("EPA4ALL_CREDENTIALS_PASSWORD", "pw");
    env.put("EPA4ALL_PROXY_ADDRESS", "env-proxy");
    env.put("EPA4ALL_PROXY_PORT", "7777");
    env.put("EPA4ALL_WORKPLACE_ID", "we");
    env.put("EPA4ALL_MANDANT_ID", "me");
    env.put("EPA4ALL_CLIENT_SYSTEM_ID", "ce");
    env.put("EPA4ALL_USER_ID", "ue");
    env.put("EPA4ALL_ADDRESS", "0.0.0.0");
    env.put("EPA4ALL_PORT", "0");
    env.put("EPA4ALL_ENVIRONMENT", "RU");
    env.put("EPA4ALL_TELEMATIK_ID", "ENV-TID");

    ConfigProvider provider = new EnvConfigProvider(CONFIG_PREFIX, env::get);
    var main = new Main(provider);

    var result = invokeLoadConfig(main, provider);

    assertEquals(URI.create("https://env.konn:1234"), result.konnektorUri());
    assertEquals("env-proxy", result.proxyAddress());
    assertEquals(7777, result.proxyPort());
    assertEquals("we", result.workplaceId());
    assertEquals("me", result.mandantId());
    assertEquals("ce", result.clientSystemId());
    assertEquals("ue", result.userId());
    assertEquals(0, result.port());
    assertEquals(Environment.RU, result.environment());
    assertEquals("ENV-TID", result.telematikId());
  }

  @Test
  void loadConfig_appliesOverrides() throws Exception {

    var keystore = createEmptyPkcs12(tempDir.resolve("keys-override.p12"), "secret");

    var cfg =
        mapProvider(
            Map.ofEntries(
                Map.entry("konnektor.uri", "https://konnektor.local:10443"),
                Map.entry("credentials.path", keystore.toString()),
                Map.entry("credentials.password", "secret"),
                Map.entry("proxy.address", "proxy.local"),
                Map.entry("proxy.port", "8888"),
                Map.entry("workplace.id", "w"),
                Map.entry("mandant.id", "m2"),
                Map.entry("client_system.id", "c2"),
                Map.entry("user.id", "u"),
                Map.entry("address", "127.0.0.1"),
                Map.entry("port", "9090"),
                Map.entry("environment", "RU"),
                Map.entry("telematik.id", "TID-123")));

    var main = new Main(cfg);
    var result = invokeLoadConfig(main, cfg);

    assertEquals(URI.create("https://konnektor.local:10443"), result.konnektorUri());
    assertEquals("proxy.local", result.proxyAddress());
    assertEquals(8888, result.proxyPort());
    assertNotNull(result.clientKeys());
    assertEquals("w", result.workplaceId());
    assertEquals("m2", result.mandantId());
    assertEquals("c2", result.clientSystemId());
    assertEquals("u", result.userId());
    assertEquals("127.0.0.1", result.address());
    assertEquals(9090, result.port());
    assertEquals(Environment.RU, result.environment());
    assertEquals("TID-123", result.telematikId());
  }

  @Test
  void loadConfig_missingKonnektorUri_throws() {
    var keystore = createEmptyPkcs12(tempDir.resolve("keys-missing-uri.p12"), "0000");
    var cfg = mapProvider(Map.of("credentials.path", keystore.toString()));
    var main = new Main(cfg);

    var ex = assertThrows(InvocationTargetException.class, () -> invokeLoadConfig(main, cfg));
    assertInstanceOf(IllegalStateException.class, ex.getCause());
    assertEquals("configuration for 'konnektor.uri' not found", ex.getCause().getMessage());
  }

  @Test
  void loadConfig_blankTelematikId_becomesNull() throws Exception {
    var keystore = createEmptyPkcs12(tempDir.resolve("keys-blank-tid.p12"), "0000");

    var cfg =
        mapProvider(
            Map.of(
                "konnektor.uri", "https://example.org:443",
                "credentials.path", keystore.toString(),
                "telematik.id", "   "));

    var main = new Main(cfg);
    var result = invokeLoadConfig(main, cfg);

    assertNull(result.telematikId());
  }

  private static ConfigProvider mapProvider(Map<String, String> values) {
    var map = new HashMap<>(values);
    return name -> Optional.ofNullable(map.get(name));
  }

  private static Main.Config invokeLoadConfig(Main main, ConfigProvider provider)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method m =
        Main.class.getDeclaredMethod(
            "loadConfig", com.oviva.telematik.epa4all.restservice.cfg.ConfigProvider.class);
    m.setAccessible(true);
    return (Main.Config) m.invoke(main, provider);
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
}
