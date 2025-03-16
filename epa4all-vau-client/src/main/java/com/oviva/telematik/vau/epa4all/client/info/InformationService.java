package com.oviva.telematik.vau.epa4all.client.info;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InformationService {

  private static final Logger log = LoggerFactory.getLogger(InformationService.class);
  private final HttpClient outerHttpClient;

  private final List<URI> epaEndpoints;

  public InformationService(
      java.net.http.HttpClient outerHttpClient,
      Environment environment,
      List<EpaProvider> providers) {

    this.outerHttpClient = outerHttpClient;
    epaEndpoints = providers.stream().map(p -> deriveEndpoint(environment, p)).toList();
  }

  public Optional<URI> findAccountEndpoint(String insurantId) {
    for (URI epaEndpoint : epaEndpoints) {
      if (hasActiveAccount(insurantId, epaEndpoint)) {
        return Optional.of(epaEndpoint);
      }
    }
    return Optional.empty();
  }

  private boolean hasActiveAccount(String insurantId, URI endpoint) {
    var req =
        HttpRequest.newBuilder(endpoint.resolve("/information/api/v1/ehr"))
            .headers("x-useragent", "Oviva/0.0.1", "x-insurantid", insurantId)
            .GET()
            .build();

    try {
      var res = outerHttpClient.send(req, HttpResponse.BodyHandlers.discarding());
      // status code mapping according to API spec
      return res.statusCode() == 204;
    } catch (IOException e) {
      log.atDebug()
          .addKeyValue("endpoint", endpoint)
          .setCause(e)
          .log("failed to reach ePA account endpoint '{}'", endpoint);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  private static URI deriveEndpoint(Environment environment, EpaProvider provider) {
    return URI.create(
        "https://epa-as-%d.%s.epa4all.de".formatted(provider.id(), environment.identifier()));
  }

  public record EpaProvider(int id) {
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Aktensystem_ePAfueralle/gemSpec_Aktensystem_ePAfueralle_V1.3.0/#A_24592-02
    public static EpaProvider IBM = new EpaProvider(1);
    public static EpaProvider BITMARCK = new EpaProvider(2);
  }

  public enum Environment {
    //    RU("ref"), // RU is not the actual RU but something else! Use "DEV" instead.
    DEV("dev"), // also known as RU2
    TEST("test"),
    PU("prod");
    private final String identifier;

    Environment(String identifier) {
      this.identifier = identifier;
    }

    public String identifier() {
      return identifier;
    }
  }
}
