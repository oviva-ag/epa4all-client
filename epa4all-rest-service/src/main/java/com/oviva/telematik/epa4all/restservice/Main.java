package com.oviva.telematik.epa4all.restservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.KonnektorServiceBuilder;
import com.oviva.epa.client.konn.KonnektorConnectionFactory;
import com.oviva.epa.client.konn.KonnektorConnectionFactoryBuilder;
import com.oviva.telematik.epa4all.client.ClientException;
import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.epa4all.client.internal.TelematikTrustRoots;
import com.oviva.telematik.epa4all.restservice.cfg.ConfigProvider;
import com.oviva.telematik.epa4all.restservice.cfg.EnvConfigProvider;
import com.oviva.telematik.epa4all.restservice.problem.BadRequestProblem;
import com.oviva.telematik.epa4all.restservice.problem.DuplicateDocumentProblem;
import com.oviva.telematik.epa4all.restservice.problem.InformationServiceUnavailableProblem;
import com.oviva.telematik.epa4all.restservice.problem.InternalErrorProblem;
import com.oviva.telematik.epa4all.restservice.problem.NotEntitledProblem;
import com.oviva.telematik.epa4all.restservice.problem.Problem;
import com.oviva.telematik.epa4all.restservice.problem.RecordUnavailableProblem;
import com.oviva.telematik.vau.epa4all.client.authz.internal.MimeTypes;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.security.*;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements AutoCloseable {

  {
    Security.addProvider(new BouncyCastleProvider());
  }

  public static final String CONFIG_PREFIX = "EPA4ALL";
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  // Retry intervals documented for getRecordStatus in I_Information_Service 1.5.1 (409: approx. 24
  // hours, 500: approx. 10 minutes), see "Retry interval" in
  // https://github.com/gematik/ePA-Basic/blob/ePA-3.1.3/src/openapi/I_Information_Service.yaml#L16-L21
  private static final long RECORD_UNAVAILABLE_RETRY_AFTER = Duration.ofHours(24).toSeconds();
  private static final long INFORMATION_SERVICE_UNAVAILABLE_RETRY_AFTER =
      Duration.ofMinutes(10).toSeconds();

  private final ConfigProvider configProvider;
  private Undertow server;

  public Main(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public static void main(String[] args) {
    logger.atDebug().log("initialising application");
    try (var app = new Main(new EnvConfigProvider(CONFIG_PREFIX, System::getenv))) {
      app.run();
      app.awaitTermination();
    } catch (Exception e) {
      logger.atError().setCause(e).log("application crashed, cause: {}", e.getMessage());
    }
  }

  public void run() {
    logger.atDebug().log("running application");

    var config = loadConfig(configProvider);
    logger.atInfo().log("config loaded: {}", config);

    var host = config.address();
    var port = config.port();

    logger.atDebug().log("booting server at http://{}:{}/", host, port);

    var clientService = buildClientService(config);
    server = buildServer(host, port, buildHandler(clientService));
    server.start();

    var actualPort = listenerAddress().getPort();
    logger.atInfo().log("server ready at http://{}:{}/", host, actualPort);
  }

  public void awaitTermination() {
    try {
      server.getWorker().awaitTermination();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public InetSocketAddress listenerAddress() {
    return (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
  }

  private Undertow buildServer(String host, int port, HttpHandler handler) {
    return Undertow.builder().addHttpListener(port, host).setHandler(handler).build();
  }

  @Override
  public void close() {
    if (server != null) {
      server.stop();
    }
  }

  private Epa4allClientService buildClientService(Config config) {
    var konnektorFactory = buildFactory(config);

    var proxySocket =
        config.proxyAddress() != null
            ? new InetSocketAddress(config.proxyAddress(), config.proxyPort())
            : null;
    return new Epa4allClientService(
        () -> buildKonnektorService(konnektorFactory, config),
        proxySocket,
        config.environment(),
        config.telematikId(),
        config.authorSpecialty());
  }

  private KonnektorService buildKonnektorService(
      KonnektorConnectionFactory factory, Config config) {
    return KonnektorServiceBuilder.newBuilder()
        .connection(factory.connect())
        .clientSystemId(config.clientSystemId())
        .mandantId(config.mandantId())
        .workplaceId(config.workplaceId())
        .userId(config.userId())
        .build();
  }

  private KonnektorConnectionFactory buildFactory(Config cfg) {
    return KonnektorConnectionFactoryBuilder.newBuilder()
        .clientKeys(cfg.clientKeys())
        .konnektorUri(cfg.konnektorUri())
        .konnektorServername(cfg.konnektorServername())
        .proxyServer(cfg.proxyAddress(), cfg.proxyPort())
        .trustManagers(cfg.konnektorTruststores())
        .build();
  }

  private List<TrustManager> loadTelematikTrustManagers(Environment env) {
    return switch (env) {
      case PU -> List.of(TelematikTrustRoots.createPuTrustManager());
      case REF, RU -> List.of(TelematikTrustRoots.createRuTrustManager());
    };
  }

  record Config(
      URI konnektorUri,
      String konnektorServername,
      List<TrustManager> konnektorTruststores,
      String proxyAddress,
      int proxyPort,
      List<KeyManager> clientKeys,
      String workplaceId,
      String mandantId,
      String clientSystemId,
      String userId,
      String address,
      int port,
      Environment environment,
      String telematikId,
      String authorSpecialty) {}

  private Config loadConfig(ConfigProvider configProvider) {

    var environment =
        configProvider
            .get("environment")
            .map(String::toUpperCase)
            .map(Environment::valueOf)
            .orElse(Environment.PU);

    var address = configProvider.get("address").orElse("0.0.0.0");
    var port = configProvider.get("port").map(Integer::parseInt).orElse(8080);

    var uri = mustLoad("konnektor.uri").map(URI::create).orElseThrow();

    var servername =
        configProvider
            .get("konnektor.servername")
            .map(String::strip)
            .filter(s -> !s.isBlank())
            // https://gemspec.gematik.de/docs/gemKPT/gemKPT_Arch_TIP/gemKPT_Arch_TIP_V2.10.0/#TIP1-A_6716
            // Der Produkttyp Konnektor MUSS es Clientsystemen ermöglichen, die LAN-seitige
            // IP-Adresse des Konnektors durch Abfrage des fest vorgegebenen FQDN "konnektor.konlan"
            // aufzulösen.
            .orElse("konnektor.konlan");

    var konnektorTrustStorePassword =
        configProvider
            .get("konnektor.truststore.password")
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .orElse("changeit");

    var konnektorTruststores =
        configProvider
            .get("konnektor.truststore.path")
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .map(Path::of)
            .map(p -> KeyStores.loadPkcs12KeyStore(p, konnektorTrustStorePassword))
            .map(KeyStores::convertToTrustManager)
            .map(List::of)
            .orElse(loadTelematikTrustManagers(environment));

    var proxyAddress = configProvider.get("proxy.address").orElse(null);

    var proxyPort = configProvider.get("proxy.port").map(Integer::parseInt).orElse(3128);

    var pw = configProvider.get("credentials.password").orElse("0000");

    var keys =
        configProvider
            .get("credentials.path")
            .map(Path::of)
            .or(() -> Optional.of(Path.of("./credentials.p12")))
            .map(p -> KeyStores.loadKeys(p, pw))
            .orElseThrow(() -> configNotValid("credentials.path"));

    var workplace = configProvider.get("workplace.id").orElse("a");

    var clientSystem = configProvider.get("client_system.id").orElse("c");

    var mandant = configProvider.get("mandant.id").orElse("m");

    var user = configProvider.get("user.id").orElse("admin");

    var telematikId =
        configProvider
            .get("telematik.id")
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .orElse(null);

    var authorSpecialty =
        configProvider.get("author.specialty").orElse("25^^^&1.3.6.1.4.1.19376.3.276.1.5.11&ISO");

    return new Config(
        uri,
        servername,
        konnektorTruststores,
        proxyAddress,
        proxyPort,
        keys,
        workplace,
        mandant,
        clientSystem,
        user,
        address,
        port,
        environment,
        telematikId,
        authorSpecialty);
  }

  private Optional<String> mustLoad(String key) {

    var v = configProvider.get(key);
    if (v.isEmpty()) {
      throwConfigNotFound(key);
    }

    return v;
  }

  private void throwConfigNotFound(String key) {
    throw new IllegalStateException("configuration for '%s' not found".formatted(key));
  }

  private RuntimeException configNotValid(String key) {
    return new IllegalStateException("configuration for '%s' not valid".formatted(key));
  }

  private HttpHandler buildHandler(Epa4allClientService clientService) {

    var om = new ObjectMapper();
    return new DisableCacheHandler(
        new BlockingHandler(
            withErrorHandling(
                Handlers.routing()
                    .get(
                        "/health",
                        ex -> {
                          if (clientService.isHealthy()) {
                            ex.setStatusCode(200).endExchange();
                          } else {
                            ex.setStatusCode(503).endExchange();
                          }
                        })
                    .post(
                        "/documents",
                        ex -> {
                          ex.startBlocking();

                          var req = om.readValue(ex.getInputStream(), DocumentCreateRequest.class);

                          var res =
                              clientService.writeDocument(
                                  req.insurantId(), req.contentType(), req.content());

                          ex.setStatusCode(201);
                          ex.getResponseHeaders()
                              .put(Headers.CONTENT_TYPE, MimeTypes.APPLICATION_JSON);
                          om.writeValue(
                              ex.getOutputStream(),
                              new WriteDocumentResponse(res.documentId().toString()));
                        })
                    .post(
                        "/documents/{document_id}",
                        ex -> {
                          var pm = ex.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
                          var documentId = uuidFromString(pm.getParameters().get("document_id"));
                          ex.startBlocking();

                          var req = om.readValue(ex.getInputStream(), DocumentCreateRequest.class);
                          var res =
                              clientService.replaceDocument(
                                  req.insurantId(), req.contentType(), req.content(), documentId);

                          ex.setStatusCode(201);
                          ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                          om.writeValue(
                              ex.getOutputStream(),
                              new WriteDocumentResponse(res.documentId().toString()));
                        }))));
  }

  HttpHandler withErrorHandling(HttpHandler next) {
    var objectMapper = new ObjectMapper();

    return exchange -> {
      try {
        next.handleRequest(exchange);
      } catch (BadRequestException e) {
        logger.atWarn().setCause(e).log("Bad request: {}", e.getMessage());

        sendProblem(exchange, objectMapper, new BadRequestProblem());
      } catch (ClientException e) {
        handleClientException(exchange, objectMapper, e);
      } catch (ApplicationException e) {
        logger.atError().setCause(e).log("Internal error: {}", e.getMessage());

        sendProblem(exchange, objectMapper, new InternalErrorProblem());
      }
    };
  }

  private void handleClientException(
      HttpServerExchange exchange, ObjectMapper objectMapper, ClientException e)
      throws JsonProcessingException {
    switch (e.reason()) {
      case RECORD_UNAVAILABLE -> {
        logger
            .atDebug()
            .setCause(e)
            .log("ePA record unavailable: {}: {}", e.getMessage(), e.getCause().getMessage());

        exchange.getResponseHeaders().put(Headers.RETRY_AFTER, RECORD_UNAVAILABLE_RETRY_AFTER);

        sendProblem(exchange, objectMapper, new RecordUnavailableProblem());
      }
      case INFORMATION_SERVICE_UNAVAILABLE -> {
        logger
            .atDebug()
            .setCause(e)
            .log(
                "ePA Information Service failed: {}: {}",
                e.getMessage(),
                e.getCause().getMessage());

        exchange
            .getResponseHeaders()
            .put(Headers.RETRY_AFTER, INFORMATION_SERVICE_UNAVAILABLE_RETRY_AFTER);

        sendProblem(exchange, objectMapper, new InformationServiceUnavailableProblem());
      }
      case INFORMATION_SERVICE_MALFORMED_REQUEST -> {
        logger
            .atError()
            .setCause(e)
            .log(
                "ePA Information Service rejected request: {}: {}",
                e.getMessage(),
                e.getCause().getMessage());

        sendProblem(exchange, objectMapper, new InternalErrorProblem());
      }
      case DUPLICATE_DOCUMENT -> {
        logger.atDebug().setCause(e).log("Duplicate document: {}", e.getMessage());

        sendProblem(exchange, objectMapper, new DuplicateDocumentProblem());
      }
      case NOT_AUTHORIZED -> {
        logger.atDebug().setCause(e).log("Not entitled: {}", e.getMessage());

        sendProblem(exchange, objectMapper, new NotEntitledProblem());
      }
      case INTERNAL -> {
        logger.atError().setCause(e).log("Internal error: {}", e.getMessage());

        sendProblem(exchange, objectMapper, new InternalErrorProblem());
      }
    }
  }

  private void sendProblem(HttpServerExchange exchange, ObjectMapper objectMapper, Problem problem)
      throws JsonProcessingException {
    exchange.setStatusCode(problem.status());
    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

    exchange.getResponseSender().send(objectMapper.writeValueAsString(problem));
  }

  private UUID uuidFromString(String uuid) {
    try {
      return UUID.fromString(uuid);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("invalid UUID", e);
    }
  }
}
