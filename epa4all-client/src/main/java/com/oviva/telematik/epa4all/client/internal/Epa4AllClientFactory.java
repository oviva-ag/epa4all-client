package com.oviva.telematik.epa4all.client.internal;

import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.epa4all.client.ClientException;
import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.epa4all.client.Epa4AllClient;
import com.oviva.telematik.epaapi.ClientConfiguration;
import com.oviva.telematik.epaapi.SoapClientFactory;
import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationService;
import com.oviva.telematik.vau.epa4all.client.authz.internal.*;
import com.oviva.telematik.vau.epa4all.client.info.InformationService;
import com.oviva.telematik.vau.httpclient.internal.DowngradeHttpClient;
import com.oviva.telematik.vau.httpclient.internal.JavaHttpClient;
import com.oviva.telematik.vau.proxy.VauProxy;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.security.*;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Epa4AllClientFactory implements AutoCloseable {

  static {
    Security.addProvider(new BouncyCastlePQCProvider());
    Security.addProvider(new BouncyCastleProvider());
  }

  private static final String LOCALHOST = "127.0.0.1";

  private static final Logger log = LoggerFactory.getLogger(Epa4AllClientFactory.class);
  private final VauProxy proxyServer;
  private final SoapClientFactory client;
  private final AuthorizationService authorizationService;
  private final InformationService informationService;
  private final SmcbCard card;

  public Epa4AllClientFactory(
      VauProxy proxyServer,
      SoapClientFactory client,
      AuthorizationService authorizationService,
      InformationService informationService,
      SmcbCard card) {
    this.proxyServer = proxyServer;
    this.client = client;
    this.authorizationService = authorizationService;
    this.informationService = informationService;
    this.card = card;
  }

  public static Epa4AllClientFactory create(
      KonnektorService konnektorService,
      InetSocketAddress konnektorProxyAddress,
      Environment environment,
      KeyStore trustStore,
      String telematikId) {

    var telematikSslContext = SslContextBuilder.buildSslContext(trustStore);
    var outerHttpClientTelematik = buildOuterHttpClient(konnektorProxyAddress, telematikSslContext);

    var informationService = buildInformationService(environment, outerHttpClientTelematik);

    var proxyServer =
        buildVauProxy(environment, konnektorProxyAddress, trustStore, telematikSslContext);

    var serverInfo = proxyServer.start();
    var vauProxyServerListener = serverInfo.listenAddress();
    var vauProxyServerAddr = new InetSocketAddress(LOCALHOST, vauProxyServerListener.getPort());

    // HTTP client used to communicate inside the VAU tunnel
    var innerVauClient = buildInnerHttpClient(vauProxyServerAddr);

    var card = findSmcBCard(konnektorService, telematikId);

    var outerHttpClient = buildOuterHttpClient(konnektorProxyAddress);

    var authorizationService =
        buildAuthorizationService(innerVauClient, outerHttpClient, konnektorService, card);

    var client =
        new SoapClientFactory(
            new ClientConfiguration(
                new InetSocketAddress(LOCALHOST, vauProxyServerListener.getPort())));

    return new Epa4AllClientFactory(
        proxyServer, client, authorizationService, informationService, card);
  }

  public Epa4AllClient newClient() {
    return new Epa4AllClientImpl(informationService, authorizationService, card, client);
  }

  private static AuthorizationService buildAuthorizationService(
      com.oviva.telematik.vau.httpclient.HttpClient innerVauClient,
      HttpClient outerHttpClient,
      KonnektorService konnektorService,
      SmcbCard card) {

    var signer = new EccSignatureAdapter(konnektorService, card);

    var authnChallengeResponder =
        new AuthnChallengeResponder(signer, new OidcClient(outerHttpClient));
    var authnClientAttester = new AuthnClientAttester(signer);
    return new AuthorizationService(
        innerVauClient, outerHttpClient, authnChallengeResponder, authnClientAttester);
  }

  public static SmcbCard findSmcBCard(KonnektorService konnektorService, String telematikId) {
    var cards = konnektorService.listSmcbCards();
    if (cards.isEmpty()) {
      throw new Epa4AllClientException("no SMC-B cards found");
    }
    if (telematikId == null) {
      if (cards.size() > 1) {
        log.atInfo().log(
            "more than one SMC-B card found, using first one - available: %s"
                .formatted(cardListToString(cards)));
      }
      return cards.getFirst();
    }

    var selectedCard = cards.stream().filter(c -> c.telematikId().equals(telematikId)).toList();
    if (selectedCard.isEmpty()) {
      throw new Epa4AllClientException(
          "no SMC-B card found for telematikId [ %s ], available %s"
              .formatted(telematikId, cardListToString(cards)));
    }
    if (selectedCard.size() > 1) {
      log.atInfo()
          .addKeyValue("telematikId", telematikId)
          .log(
              "more than one SMC-B card found for telematikId [ %s ], using first one - available: %s"
                  .formatted(telematikId, cardListToString(cards)));
    }

    return selectedCard.getFirst();
  }

  private static String cardListToString(List<SmcbCard> cards) {
    return "[ "
        + cards.stream().map(Epa4AllClientFactory::cardToString).collect(Collectors.joining(", "))
        + " ]";
  }

  private static String cardToString(SmcbCard card) {
    return "'" + card.telematikId() + "' (" + card.holderName() + ")";
  }

  private static VauProxy buildVauProxy(
      Environment environment,
      InetSocketAddress konnektorProxyAddress,
      KeyStore trustStore,
      SSLContext sslContext) {

    var isPu = environment == Environment.PU;
    var xUserAgent = isPu ? "GEMOvivepa4fA734EBIP/0.1.0" : "GEMOvivepa4fA1d5W8sR/0.1.0";
    return new VauProxy(
        new VauProxy.Configuration(
            konnektorProxyAddress, 0, isPu, xUserAgent, sslContext, trustStore));
  }

  private static InformationService buildInformationService(
      Environment environment, HttpClient outerHttpClient) {

    var providers =
        List.of(InformationService.EpaProvider.IBM, InformationService.EpaProvider.BITMARCK);

    var informationServiceEnvironment =
        switch (environment) {
          case PU -> InformationService.Environment.PU;
          case RU -> InformationService.Environment.DEV;
        };

    return new InformationService(outerHttpClient, informationServiceEnvironment, providers);
  }

  private static com.oviva.telematik.vau.httpclient.HttpClient buildInnerHttpClient(
      InetSocketAddress vauProxyServerAddress) {

    // HTTP client used to communicate inside the VAU tunnel
    var innerHttpClient =
        HttpClient.newBuilder()
            // this is the local VAU termination proxy
            .proxy(ProxySelector.of(vauProxyServerAddress))
            // no redirects, wee need to deal with redirects from authorization directly
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(7))
            // within the VAU tunnel HTTP/1.1 is preferred
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    // we need to downgrade HTTPS requests to HTTP, otherwise the proxy can't deal with the requests
    return new DowngradeHttpClient(JavaHttpClient.from(innerHttpClient));
  }

  private static HttpClient buildOuterHttpClient(InetSocketAddress konnektorProxyAddress) {
    try {
      return buildOuterHttpClient(konnektorProxyAddress, SSLContext.getDefault());
    } catch (NoSuchAlgorithmException e) {
      throw new ClientException("failed creating client", e);
    }
  }

  private static HttpClient buildOuterHttpClient(
      InetSocketAddress konnektorProxyAddress, SSLContext sslContext) {

    return HttpClient.newBuilder()
        .sslContext(sslContext)
        // Returned URLs actually need to be resolved via the
        // proxy, their FQDN is only resolved within the TI
        .proxy(ProxySelector.of(konnektorProxyAddress))
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  @Override
  public void close() {
    proxyServer.stop();
  }
}
