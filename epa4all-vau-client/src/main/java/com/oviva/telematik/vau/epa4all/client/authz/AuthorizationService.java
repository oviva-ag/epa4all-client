package com.oviva.telematik.vau.epa4all.client.authz;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.*;
import com.oviva.telematik.vau.epa4all.client.authz.internal.*;
import com.oviva.telematik.vau.httpclient.HttpClient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * see <a href="https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_FD/latest/#5.5">here</a>.
 *
 * <p>Authorization with a smartcard (SMC-B) equipped client of a health care institution and login
 * to a health record system.
 *
 * <p>This authorization method addresses the central smartcard IDP. A successful login provides an
 * ID-Token and causes an authorized epa user session for the health care institution allowing
 * access to several health records on demand.
 *
 * <p>Client Attestation The authorization flow uses client attestation to guarantee a match of IDP
 * issued ID-Token and the user's identity of the authenticated clients.
 *
 * <p>A client first retrieves a nonce value from the authorization service. The nonce is signed
 * with the clients SMC-B and becomes the client attestation. In a final sendAuthCodeSC operation
 * the client attestation is compared by the authorization service with the issued nonce value and
 * attestation telematik-id matching ID-Token telematik-id is verified.
 */
public class AuthorizationService {

  private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

  private final HttpClient innerHttpClient;
  private final java.net.http.HttpClient outerHttpClient;
  private final AuthnChallengeResponder authnChallengeResponder;
  private final AuthnClientAttester authnClientAttester;

  public AuthorizationService(
      HttpClient innerHttpClient,
      java.net.http.HttpClient outerHttpClient,
      AuthnChallengeResponder authnChallengeResponder,
      AuthnClientAttester authnClientAttester) {
    this.innerHttpClient = innerHttpClient;
    this.outerHttpClient = outerHttpClient;
    this.authnChallengeResponder = authnChallengeResponder;
    this.authnClientAttester = authnClientAttester;
  }

  public void authorizeVauWithSmcB(URI vauEndpoint, String insurantId) {

    var nonceRes = getNonce(vauEndpoint, insurantId);
    var nonce = nonceRes.nonce();

    var authRes = sendAuthorizationRequestSmcB(vauEndpoint, insurantId);

    // A_20663-01 & A_20665-01
    var challengeResponse = authnChallengeResponder.challengeResponse(authRes.challenge());

    var idpBaseUri = challengeResponse.issuer();
    var authorizationCode =
        exchangeEncryptedSignedChallenge(idpBaseUri, challengeResponse.response());

    var signedClientAttest = authnClientAttester.attestClient(nonce);
    var signedClientAttestB64 = signedClientAttest.serialize();

    if (log.isDebugEnabled()) {
      log.atDebug().log(
          "signed nonce\nauthorizationCode\n===\n{}\n===\n\nclientAttest\n===\n{}\n===\n",
          authorizationCode,
          signedClientAttestB64);
    }

    sendAuthorizationCodeSmbC(vauEndpoint, authorizationCode, signedClientAttestB64, insurantId);
  }

  private AuthorizationRequestResponse sendAuthorizationRequestSmcB(
      URI vauEndpoint, String insurantId) {

    var path = "/epa/authz/v1/send_authorization_request_sc";
    var uri = vauEndpoint.resolve(path);
    var method = "GET";
    var req =
        new HttpClient.Request(
            uri, method, List.of(new HttpClient.Header("x-insurantid", insurantId)), null);

    var res = innerHttpClient.call(req);
    if (res.status() != 302) {
      throw new IllegalStateException(
          "unexpected status '%s %s' %d".formatted(method, path, res.status()));
    }
    var location = parseLocationHeader(res);
    // TODO: more error checking in redirect_uri

    return followRedirect(location);
  }

  private URI parseLocationHeader(HttpClient.Response res) {

    return res.headers().stream()
        .filter(h -> "location".equalsIgnoreCase(h.name()))
        .map(HttpClient.Header::value)
        .findFirst()
        .map(URI::create)
        .orElseThrow(() -> new AuthorizationException("missing 'Location' header"));
  }

  private String exchangeEncryptedSignedChallenge(URI idpBaseUri, String encryptedSignedChallenge) {

    // TODO: dynamic config, though as is it is a JWT signed with `alg=BP256R1` which is
    // non-standard (╯°□°)╯︵ ┻━┻
    // https://idp-ref.app.ti-dienste.de/.well-known/openid-configuration
    // take 'authorization_endpoint'
    var uri = idpBaseUri.resolve("/auth");

    var body =
        "signed_challenge=%s"
            .formatted(URLEncoder.encode(encryptedSignedChallenge, StandardCharsets.UTF_8));

    var req =
        HttpRequest.newBuilder(uri)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .headers("content-type", "application/x-www-form-urlencoded")
            .build();

    try {
      var res = outerHttpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 302) {
        throw new AuthorizationException(
            "unexpected status code in authorization response: %d".formatted(res.statusCode()));
      }

      var location =
          res.headers()
              .firstValue("Location")
              .map(URI::create)
              .orElseThrow(
                  () ->
                      new AuthorizationException(
                          "missing 'Location' header in authorization response"));

      var queryParams = UriQueryUtil.parse(location);
      validateAuthCodeResponse(queryParams);

      return getCodeFromParams(queryParams)
          .orElseThrow(
              () ->
                  new AuthorizationException(
                      "authorization response has no valid 'code' parameter"));

    } catch (IOException e) {
      throw new AuthorizationException("failed to get authorization response", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    throw new AuthorizationException("unexpected error");
  }

  private void validateAuthCodeResponse(Map<String, String> params) {
    // https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1
    if (!params.containsKey("error")) {
      return;
    }

    throw new AuthorizationException(
        "error in authorization response 'error=%s'".formatted(params.get("error")));
  }

  private Optional<String> getCodeFromParams(Map<String, String> params) {
    return Optional.ofNullable(params.get("code"));
  }

  public NonceResponse getNonce(URI vauEndpoint, String insurantId) {

    var path = "/epa/authz/v1/getNonce";
    var nonceUri = vauEndpoint.resolve(path);
    var method = "GET";
    var req1 =
        new HttpClient.Request(
            nonceUri,
            method,
            List.of(
                new HttpClient.Header("accept", "application/json"),
                new HttpClient.Header("x-insurantid", insurantId)),
            null);

    var res = innerHttpClient.call(req1);
    if (res.status() != 200) {
      throw new IllegalStateException(
          "unexpected status '%s %s' %d".formatted(method, path, res.status()));
    }

    return JsonCodec.readBytes(res.body(), NonceResponse.class);
  }

  private AuthorizationRequestResponse followRedirect(URI idpAuthEndpoint) {
    try {
      var res =
          outerHttpClient.send(
              HttpRequest.newBuilder(idpAuthEndpoint).GET().build(),
              HttpResponse.BodyHandlers.ofByteArray());
      if (res.statusCode() != 200) {
        throw new IllegalStateException(
            "unexpected status %s %s, got: %s".formatted("GET", idpAuthEndpoint, res.statusCode()));
      }
      return JsonCodec.readBytes(res.body(), AuthorizationRequestResponse.class);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("unreachable");
    }
  }

  private void sendAuthorizationCodeSmbC(
      URI vauEndpoint, String authorizationCode, String clientAttest, String insurantId) {

    // A_24766

    var path = "/epa/authz/v1/send_authcode_sc";
    var uri = vauEndpoint.resolve(path);
    var method = "POST";

    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_FD/latest/#5.5
    var body = new SendAuthcodeSmbCBody(authorizationCode, clientAttest);
    var reqBody = JsonCodec.writeBytes(body);

    var req =
        new HttpClient.Request(
            uri,
            method,
            List.of(
                new HttpClient.Header("content-type", "application/json"),
                new HttpClient.Header("x-insurantid", insurantId),
                new HttpClient.Header("accept", "application/json")),
            reqBody);

    var res = innerHttpClient.call(req);
    if (res.status() != 200) {
      if (log.isDebugEnabled()) {
        log.atDebug().log(
            "received bad status code, client -> VAU request:\n{}\nVAU -> client response:\n{}",
            body,
            res.body() != null ? new String(res.body(), StandardCharsets.UTF_8) : "");
      }
      throw new IllegalStateException(
          "unexpected status '%s %s' %d".formatted(method, path, res.status()));
    }
  }

  record SendAuthcodeSmbCBody(
      @JsonProperty("authorizationCode") String authorizationCode,
      @JsonProperty("clientAttest") String clientAttest) {}
}
