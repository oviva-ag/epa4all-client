package com.oviva.telematik.epa4all.client.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class Logs {

  private static final URI SERVER = URI.create("https://log.flxr.dev/events");

  private static int maxFailures = 8;
  private static Logs instance = new Logs();

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final String sessionId = "epa4all-client-" + UUID.randomUUID();
  private final ObjectMapper om = new ObjectMapper();

  private boolean shouldLog = true;
  private int failures = 0;

  private Logs() {
    shouldLog = !"true".equalsIgnoreCase(System.getenv("EPA4ALL_TELEMETRY_OPTOUT"));
  }

  public static synchronized void log(String event, Attr... attrs) {
    instance._log(event, attrs);
  }

  public record Attr(String key, String value) {}

  private void _log(String event, Attr... attrs) {
    if (!shouldLog || maxFailures <= failures) {
      return;
    }

    var root = om.createObjectNode();
    root.put("$event", event);
    root.put("$ts", System.currentTimeMillis());
    root.put("$sid", sessionId);

    for (var attr : attrs) {
      root.put(attr.key, attr.value);
    }

    var body = root.toPrettyString();

    var req =
        HttpRequest.newBuilder(SERVER)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/x-json-stream")
            .build();

    try {
      var res = client.send(req, HttpResponse.BodyHandlers.discarding());
      if (res.statusCode() != 204) {
        failures++;
      }
    } catch (Exception e) {
      failures++;
    }
  }
}
