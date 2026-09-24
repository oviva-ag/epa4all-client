package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static java.util.function.Predicate.not;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.JwkParser;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;

public class OidcClient {

  private final HttpClient httpClient;
  private final DiscoveryValidator discoveryValidator;

  public OidcClient(HttpClient httpClient, DiscoveryValidator discoveryValidator) {
    this.httpClient = httpClient;
    this.discoveryValidator = discoveryValidator;
  }

  public record OidcDiscoveryResponse(
      @JsonProperty("issuer") URI issuer,
      @JsonProperty("iat") Instant iat,
      @JsonProperty("exp") Instant exp,
      @JsonProperty("uri_puk_idp_enc") URI uriPukIdpEnc,
      @JsonProperty("uri_puk_idp_sig") URI uriPukIdpSig,
      @JsonProperty("jwks_uri") URI jwksUri) {}

  public OidcDiscoveryResponse fetchOidcDiscoveryDocument(@NonNull URI issuer) {

    try {
      var discoveryUrl = OIDCProviderMetadata.resolveURL(new Issuer(issuer)).toURI();
      var request =
          HttpRequest.newBuilder()
              .uri(discoveryUrl)
              .GET()
              .timeout(Duration.ofSeconds(10))
              .header("Accept", MimeTypes.APPLICATION_JSON)
              .build();

      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      verifyOk(response);

      return parseResponse(response);
    } catch (GeneralException | URISyntaxException | IOException | InterruptedException e) {
      throw new AuthorizationException("Failed to parse OIDC discovery document", e);
    }
  }

  private OidcDiscoveryResponse parseResponse(HttpResponse<String> response) {
    var contentType =
        response
            .headers()
            .firstValue("content-type")
            .map(ct -> ct.split(";")[0].trim().toLowerCase())
            .filter(not(String::isEmpty))
            .orElseThrow(
                () ->
                    new AuthorizationException(
                        "Missing content-type header in discovery document response"));

    if (contentType.equals("application/jwt")) {
      return verifyAndParse(response.body());
    }
    throw new AuthorizationException(
        "Unsupported content-type in discovery document response: " + contentType);
  }

  private OidcDiscoveryResponse verifyAndParse(String jwt) {
    try {

      // establish trust in the discovery document
      discoveryValidator.validate(SignedJWT.parse(jwt));

      var payload = SignedJWT.parse(jwt).getPayload();
      var discoveryResponse = JsonCodec.readBytes(payload.toBytes(), OidcDiscoveryResponse.class);

      /*
       * Prevent SSRF: the discovery document is otherwise trusted (valid signature from a TI
       * IDP-Dienst certificate), but its key URIs must not be able to point anywhere other than
       * the issuer's own host. Without this, a manipulated/malicious discovery response could
       * redirect the subsequent, non-blind fetchJwk() request to an arbitrary host.
       */
      verifySameHostAsIssuer(discoveryResponse);

      return discoveryResponse;
    } catch (ParseException e) {
      throw new AuthorizationException("Failed to parse JWT", e);
    }
  }

  private void verifySameHostAsIssuer(OidcDiscoveryResponse discoveryResponse) {
    var issuerHost =
        discoveryResponse.issuer() != null ? discoveryResponse.issuer().getHost() : null;
    if (issuerHost == null) {
      throw new AuthorizationException("Discovery document is missing an issuer");
    }

    verifySameHost(issuerHost, discoveryResponse.jwksUri());
    verifySameHost(issuerHost, discoveryResponse.uriPukIdpEnc());
    verifySameHost(issuerHost, discoveryResponse.uriPukIdpSig());
  }

  private void verifySameHost(String issuerHost, URI uri) {
    if (uri == null) {
      return;
    }
    if (!issuerHost.equalsIgnoreCase(uri.getHost())) {
      throw new AuthorizationException(
          "Discovery document key URI host '%s' does not match issuer host '%s'"
              .formatted(uri.getHost(), issuerHost));
    }
  }

  /** Fetches the JWK Set from the JWKS URI in the discovery document. */
  public JWK fetchJwk(@NonNull URI uri) {
    try {

      var request =
          HttpRequest.newBuilder()
              .uri(uri)
              .GET()
              .timeout(Duration.ofSeconds(10))
              .header("Accept", MimeTypes.APPLICATION_JSON)
              .build();

      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      verifyOk(response);
      verifyContentType(response, MimeTypes.APPLICATION_JSON);

      return JwkParser.parseJwk(response.body());

    } catch (IOException e) {
      throw new AuthorizationException("Failed to fetch JWK Set", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private <T> void verifyOk(HttpResponse<T> res) {
    if (res.statusCode() != 200) {
      throw new AuthorizationException(
          "Failed to fetch " + res.uri() + " Set. Status code: " + res.statusCode());
    }
  }

  private <T> void verifyContentType(HttpResponse<T> res, String expectedContentType) {
    var raw =
        res.headers()
            .firstValue("content-type")
            .orElseThrow(
                () ->
                    new AuthorizationException(
                        "Missing content-type header in %s".formatted(res.uri())));
    var contentType = raw.split(";")[0].trim().toLowerCase();
    if (!contentType.equals(expectedContentType)) {
      throw new AuthorizationException(
          "Unexpected content-type in %s Set. Expected: %s, got: %s"
              .formatted(res.uri(), expectedContentType, contentType));
    }
  }

  public interface DiscoveryValidator {
    void validate(SignedJWT jwsObject);
  }
}
