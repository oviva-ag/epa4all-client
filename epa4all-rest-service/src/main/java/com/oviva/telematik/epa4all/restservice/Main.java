package com.oviva.telematik.epa4all.restservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.KonnektorServiceBuilder;
import com.oviva.epa.client.konn.KonnektorConnectionFactory;
import com.oviva.epa.client.konn.KonnektorConnectionFactoryBuilder;
import com.oviva.telematik.epa4all.client.ClientException;
import com.oviva.telematik.epa4all.client.DuplicateDocumentClientException;
import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.epa4all.client.NotAuthorizedClientException;
import com.oviva.telematik.epa4all.restservice.cfg.ConfigProvider;
import com.oviva.telematik.epa4all.restservice.cfg.EnvConfigProvider;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.DisableCacheHandler;
import io.undertow.util.PathTemplateMatch;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import javax.net.ssl.KeyManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements AutoCloseable {

  {
    Security.addProvider(new BouncyCastleProvider());
    Security.addProvider(new BouncyCastlePQCProvider());
  }

  public static final String CONFIG_PREFIX = "EPA4ALL";
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

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
        () -> buildKonnektorService(konnektorFactory, config), proxySocket, config.environment());
  }

  private KonnektorService buildKonnektorService(
      KonnektorConnectionFactory factory, Config config) {
    return KonnektorServiceBuilder.newBuilder()
        .connection(factory.connect())
        .clientSystemId(config.clientSystemId)
        .mandantId(config.mandantId())
        .workplaceId(config.workplaceId())
        .userId(config.userId())
        .build();
  }

  private KonnektorConnectionFactory buildFactory(Config cfg) {
    return KonnektorConnectionFactoryBuilder.newBuilder()
        .clientKeys(cfg.clientKeys())
        .konnektorUri(cfg.konnektorUri())
        .proxyServer(cfg.proxyAddress(), cfg.proxyPort())
        .trustAllServers() // currently we don't validate the server's certificate
        .build();
  }

  record Config(
      URI konnektorUri,
      String proxyAddress,
      int proxyPort,
      List<KeyManager> clientKeys,
      String workplaceId,
      String mandantId,
      String clientSystemId,
      String userId,
      String address,
      int port,
      Environment environment) {}

  private Config loadConfig(ConfigProvider configProvider) {

    var address = configProvider.get("address").orElse("0.0.0.0");
    var port = configProvider.get("port").map(Integer::parseInt).orElse(8080);

    var uri = mustLoad("konnektor.uri").map(URI::create).orElseThrow();

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

    var environment =
        configProvider
            .get("environment")
            .map(String::toUpperCase)
            .map(Environment::valueOf)
            .orElse(Environment.PU);

    return new Config(
        uri,
        proxyAddress,
        proxyPort,
        keys,
        workplace,
        mandant,
        clientSystem,
        user,
        address,
        port,
        environment);
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

                          om.writeValue(
                              ex.getOutputStream(),
                              new WriteDocumentResponse(res.documentId().toString()));
                          ex.setStatusCode(201);
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

                          om.writeValue(
                              ex.getOutputStream(),
                              new WriteDocumentResponse(res.documentId().toString()));
                          ex.setStatusCode(201);
                        }))));
  }

  private HttpHandler withErrorHandling(HttpHandler next) {
    // TODO problem response?
    return exchange -> {
      try {
        next.handleRequest(exchange);
      } catch (BadRequestException e) {
        logger.atDebug().setCause(e).log("bad request: {}", e.getMessage());
        exchange.setStatusCode(400).endExchange();
      } catch (DuplicateDocumentClientException e) {
        logger.atDebug().setCause(e).log("conflict: {}", e.getMessage());
        exchange.setStatusCode(409).endExchange();
      } catch (NotAuthorizedClientException e) {
        logger.atDebug().setCause(e).log("not authorized: {}", e.getMessage());
        exchange.setStatusCode(403).endExchange();
      } catch (ApplicationException | ClientException e) {
        logger.atError().setCause(e).log("internal error: {}", e.getMessage());
        exchange.setStatusCode(500).endExchange();
      }
    };
  }

  private UUID uuidFromString(String uuid) {
    try {
      return UUID.fromString(uuid);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("invalid UUID", e);
    }
  }
}
