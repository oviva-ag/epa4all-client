package com.oviva.telematik.vau.httpclient.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.oviva.telematik.vau.httpclient.*;
import de.gematik.vau.lib.VauClientStateMachine;
import de.gematik.vau.lib.exceptions.VauProtocolException;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionFactory implements VauClientFactory {

  private static final Logger log = LoggerFactory.getLogger("vau-channel");
  private static final String METHOD_POST = "POST";

  // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/latest/#A_24608
  private static final int VAU_CID_MAX_BYTE_LENGTH = 200;
  private static final Pattern VAU_CID_PATTERN = Pattern.compile("/[A-Za-z0-9-/]+");

  private final HttpClient outerClient;
  private final List<HttpHeader> userAgentHeaders;

  private final SignedPublicKeysTrustValidatorFactory signedPublicKeysTrustValidatorFactory;

  /**
   * @param xUserAgent as registered with Gematik, CLIENTID1234567890AB/2.1.12-45
   */
  public ConnectionFactory(
      HttpClient outerClient,
      String xUserAgent,
      SignedPublicKeysTrustValidatorFactory signedPublicKeysTrustValidatorFactory) {
    this.userAgentHeaders =
        List.of(
            new HttpHeader("X-Useragent", xUserAgent), new HttpHeader("User-Agent", xUserAgent));
    this.outerClient = new HeaderDecoratorHttpClient(outerClient, userAgentHeaders);
    this.signedPublicKeysTrustValidatorFactory = signedPublicKeysTrustValidatorFactory;
  }

  /**
   * Initializes a new "Vertrauenswuerdige Ausfuehrungsumgebung" (VAU), roughly translates to a
   * Trusted Execution Environment (TEE).
   *
   * @return a framed connection allowing clients to send binary data to a VAU and receive a binary
   *     response.
   */
  public HttpClient connect(URI vauUri) {

    var client = signedPublicKeysTrustValidatorFactory.create(vauUri);

    if (log.isDebugEnabled()) {
      log.atDebug().log("starting VAU handshake");
    }

    var result = handshake(vauUri.resolve("/VAU"), client);

    if (log.isDebugEnabled()) {
      log.atDebug().log("successful VAU handshake");
    }

    var innerClient =
        new VauHttpClientImpl(
            new Connection(outerClient, result.cid(), result.sessionUri(), client));

    // user-agent headers: A_24677 & A_22470
    return new HeaderDecoratorHttpClient(innerClient, userAgentHeaders);
  }

  /** does the handshake to initialize the trusted environment */
  private HandshakeResult handshake(URI vauUri, VauClientStateMachine client) {

    var msg1 = client.generateMessage1();
    var msg2 = postMsg1(vauUri, msg1);

    var cid = msg2.cid();
    validateCid(cid);

    var msg3 = client.receiveMessage2(msg2.body());

    var msg4 = postMsg3(vauUri, cid, msg3);
    client.receiveMessage4(msg4.body());

    return new HandshakeResult(cid, msg4.sessionUri());
  }

  record HandshakeResult(String cid, URI sessionUri) {}

  private Msg2 postMsg1(URI uri, byte[] body) {

    log.atDebug().log("handshake: -> msg1");

    var res = postCbor(uri, body);
    var vauCid =
        res.headers().stream()
            .filter(h -> "VAU-CID".equalsIgnoreCase(h.name()))
            .map(HttpHeader::value)
            .findFirst();

    log.atDebug().log("handshake: <- msg2");
    return new Msg2(res.body(), vauCid.orElse(null));
  }

  private HttpResponse postCbor(URI uri, byte[] body) {

    log.atDebug()
        .setMessage("handshake: -> POST {}\n{}")
        .addArgument(uri)
        .addArgument(() -> cborBytesToJsonString(body))
        .log();

    var req =
        new HttpRequest(
            uri, METHOD_POST, List.of(new HttpHeader("Content-Type", "application/cbor")), body);

    var res = outerClient.call(req);
    if (res.status() != 200) {
      throw new HttpExceptionWithInfo(
          res.status(),
          METHOD_POST,
          uri,
          "bad status got: %d , expected: 200".formatted(res.status()));
    }

    log.atDebug()
        .setMessage("handshake: <- {}\n{}")
        .addArgument(uri)
        .addArgument(() -> cborBytesToJsonString(res.body()))
        .log();

    return res;
  }

  @SuppressWarnings("java:S6218")
  private record Msg2(byte[] body, String cid) {}

  private Msg4 postMsg3(URI vauUri, String cid, byte[] msg3) {

    log.atDebug().log("handshake: -> msg3");
    var sessionUri = vauUri.resolve(cid);
    var msg4 = postCbor(sessionUri, msg3).body();

    log.atDebug().log("handshake: <- msg4");
    return new Msg4(msg4, sessionUri);
  }

  @SuppressWarnings("java:S6218")
  private record Msg4(byte[] body, URI sessionUri) {}

  private void validateCid(String cid) {
    if (cid == null) {
      throw new VauProtocolException("missing VAU-CID in handshake");
    }
    if (cid.length() > VAU_CID_MAX_BYTE_LENGTH) {
      throw new VauProtocolException(
          "invalid VAU-CID in handshake, too long %d > %d "
              .formatted(cid.length(), VAU_CID_MAX_BYTE_LENGTH));
    }
    if (!VAU_CID_PATTERN.matcher(cid).matches()) {
      throw new VauProtocolException("invalid VAU-CID in handshake: '%s'".formatted(cid));
    }
  }

  @SuppressWarnings("java:S1166")
  private String cborBytesToJsonString(byte[] cborBytes) {

    var cm =
        CBORMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    var om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    try {
      var tres = cm.readTree(cborBytes);
      return om.writeValueAsString(tres);
    } catch (IOException e) {
      return "<invalid cbor bytes>";
    }
  }
}
