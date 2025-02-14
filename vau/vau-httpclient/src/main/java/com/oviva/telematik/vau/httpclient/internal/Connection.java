package com.oviva.telematik.vau.httpclient.internal;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import de.gematik.vau.lib.VauClientStateMachine;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Connection {

  private static final Logger log = LoggerFactory.getLogger("vau-messages");
  private static final String METHOD_POST = "POST";

  private final HttpClient outerClient;
  private final String cid;
  private final URI sessionUri;
  private final VauClientStateMachine client;

  public Connection(
      HttpClient outerClient, String cid, URI sessionUri, VauClientStateMachine client) {
    this.outerClient = outerClient;
    this.cid = cid;
    this.sessionUri = sessionUri;
    this.client = client;
  }

  public byte[] call(byte[] requestBody) {

    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/latest/#A_24628-01

    var ciphertextRequest = client.encryptVauMessage(requestBody);
    var req =
        new HttpRequest(
            sessionUri,
            METHOD_POST,
            List.of(
                new HttpHeader("content-type", "application/octet-stream"),
                new HttpHeader("accept", "*/*")),
            ciphertextRequest);

    if (log.isDebugEnabled()) {
      log.atDebug().log("> VAU message: {} {}", req.method(), req.uri());
    }

    var res = outerClient.call(req);

    if (log.isDebugEnabled()) {
      log.atDebug().log("< VAU message: status={}", res.status());
    }

    if (res.status() != 200) {
      throw new HttpExceptionWithInfo(
          res.status(),
          METHOD_POST,
          sessionUri,
          "bad status code %d != 200, cid=%s".formatted(res.status(), cid));
    }
    var ciphertextResponse = res.body();
    return client.decryptVauMessage(ciphertextResponse);
  }
}
