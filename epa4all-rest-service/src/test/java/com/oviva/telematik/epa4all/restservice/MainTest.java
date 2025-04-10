package com.oviva.telematik.epa4all.restservice;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("e2e")
class MainTest {

  private static final CountDownLatch exit = new CountDownLatch(1);

  @Test
  void bootsFine() throws IOException, InterruptedException {
    bootApp();

    var client = HttpClient.newHttpClient();
    var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/health")).GET().build();

    var deadline = Instant.now().plusSeconds(10);
    var success = false;
    while (deadline.isAfter(Instant.now())) {
      var res = client.send(req, HttpResponse.BodyHandlers.discarding());
      if (res.statusCode() == 200) {
        success = true;
        break;
      }
      Thread.sleep(300);
    }

    assertTrue(success);

    exit.countDown();
  }

  private static void bootApp() throws IOException {

    var config = new Properties();
    config.load(
        new StringReader(
            """
            konnektor.uri=https://10.156.145.103:443
            proxy.address=127.0.0.1
            environment=RU
            """));

    var executor = Executors.newFixedThreadPool(1);

    var started = new CountDownLatch(1);
    executor.execute(
        () -> {
          try (var m = new Main(k -> Optional.ofNullable(config.getProperty(k)))) {
            m.run();
            started.countDown();
            exit.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    var ok = false;
    try {
      ok = started.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!ok) {
      fail("server failed to boot within timeout");
    }
  }
}
