package com.oviva.telematik.vau.epa4all.client.authz.internal;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyConverter;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import com.oviva.telematik.vau.epa4all.client.authz.EccSignatureService;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BP256ECDHEncrypter;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BP256ECKey;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolAlgorithms;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolJwsVerifier;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthnChallengeResponder {

  private final EccSignatureService eccSignatureService;
  private final OidcClient oidcClient;
  private final Logger log = LoggerFactory.getLogger(AuthnChallengeResponder.class);

  public AuthnChallengeResponder(EccSignatureService eccSignatureService, OidcClient oidcClient) {
    this.eccSignatureService = eccSignatureService;
    this.oidcClient = oidcClient;
  }

  public record Response(URI issuer, String response) {}

  public Response challengeResponse(String challenge) {

    // A_20663-01
    var parsedChallenge = parseAndValidateChallenge(challenge);
    var iss = issuerUriFromChallenge(parsedChallenge);

    // A_20665-01
    var jweResponse = encryptAndSignChallenge(iss, parsedChallenge);

    return new Response(iss, jweResponse.serialize());
  }

  private URI issuerUriFromChallenge(SignedJWT challenge) {
    try {
      return URI.create(challenge.getJWTClaimsSet().getIssuer());
    } catch (ParseException e) {
      throw new AuthorizationException("failed to parse challenge issuer", e);
    }
  }

  private SignedJWT parseAndValidateChallenge(String challenge) {
    // A_20663-01

    var parsedChallengeJwt = parseChallenge(challenge);
    validateChallenge(parsedChallengeJwt);

    return parsedChallengeJwt;
  }

  private void validateChallenge(SignedJWT challenge) {

    try {
      // Note: The challenge contains the URL to the keys, so is anyways in control of it. Not sure
      // what validating the signature adds.
      var claims = challenge.getJWTClaimsSet();
      var iss = claims.getIssuer();

      var discoveryDocument = oidcClient.fetchOidcDiscoveryDocument(URI.create(iss));
      var jwk = oidcClient.fetchJwk(discoveryDocument.uriPukIdpSig());

      var jwsKeySelector = keySelector(jwk);

      var jwtProcessor = new DefaultJWTProcessor<>();
      jwtProcessor.setJWSVerifierFactory(new BrainpoolJwsVerifier.Factory());
      jwtProcessor.setJWSKeySelector(jwsKeySelector);
      jwtProcessor.setJWTClaimsSetVerifier(
          new DefaultJWTClaimsVerifier<>(
              new JWTClaimsSet.Builder().build(), Set.of(JWTClaimNames.ISSUER)));

      // validate signature of challenge A_20663-01
      jwtProcessor.process(challenge, null);

    } catch (ParseException | BadJOSEException | JOSEException e) {
      throw new AuthorizationException("failed to verify challenge signature", e);
    }
  }

  private JWSKeySelector<SecurityContext> keySelector(JWK jwk) {
    return new SingleKeyJWSKeySelector<>(
        BrainpoolAlgorithms.BS256R1, KeyConverter.toJavaKeys(List.of(jwk)).get(0));
  }

  private SignedJWT parseChallenge(String challenge) {
    try {
      var parsedChallenge = SignedJWT.parse(challenge);
      return validateChallengeTypeAndClaims(parsedChallenge);
    } catch (BadJOSEException | ParseException e) {
      throw new AuthorizationException("challenge is not a valid JWT", e);
    }
  }

  private SignedJWT validateChallengeTypeAndClaims(SignedJWT challenge)
      throws BadJOSEException, ParseException {

    var challengeTypeVerifier = new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT);

    var challengeClaimsVerifier =
        new DefaultJWTClaimsVerifier<>(
            new JWTClaimsSet.Builder().claim("response_type", "code").build(),
            Set.of(JWTClaimNames.ISSUED_AT, JWTClaimNames.ISSUER, JWTClaimNames.EXPIRATION_TIME));

    challengeTypeVerifier.verify(challenge.getHeader().getType(), null);
    challengeClaimsVerifier.verify(challenge.getJWTClaimsSet(), null);
    return challenge;
  }

  public JWEObject encryptAndSignChallenge(URI iss, @NonNull SignedJWT challenge) {
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Dienst/gemSpec_IDP_Dienst_V1.7.0/#7.3

    var expiry = expiryFromChallengeBody(challenge);
    var payload = signChallenge(challenge.serialize());

    try {

      // https://gemspec.gematik.de/docs/gemILF/gemILF_PS_ePA/gemILF_PS_ePA_V3.2.3/#A_20667-02
      var idpEncKey = fetchIdpEncKey(iss);

      if (!(idpEncKey instanceof BP256ECKey bp256ECKey)) {
        throw new AuthorizationException(
            "unexpected idp enc key type: %s".formatted(idpEncKey.getKeyType().getValue()));
      }

      var pub = bp256ECKey.toECPublicKey(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME));

      // extra hoops for BP256 signing
      var encrypter = new BP256ECDHEncrypter(pub);
      encrypter
          .getJCAContext()
          .setProvider(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME));

      // https://datatracker.ietf.org/doc/html/draft-yusef-oauth-nested-jwt-03
      var jwe = nestAsJwe(payload, expiry);
      jwe.encrypt(encrypter);

      debugLogChallengeJwe(jwe);

      return jwe;
    } catch (JOSEException e) {
      throw new AuthorizationException("Failed to encrypt and sign challenge", e);
    }
  }

  private JWK fetchIdpEncKey(URI issuer) {

    // examples:
    // RU: https://idp-ref.zentral.idp.splitdns.ti-dienste.de/.well-known/openid-configuration
    // PU: https://idp.zentral.idp.splitdns.ti-dienste.de/.well-known/openid-configuration

    // we do not verify the document much here, we just want the links
    var discoveryDocument = oidcClient.fetchOidcDiscoveryDocument(issuer);

    var encKeyUri = discoveryDocument.uriPukIdpEnc();
    if (encKeyUri == null) {
      throw new AuthorizationException("no uri_puk_idp_enc found in discovery document");
    }

    // https://gemspec.gematik.de/docs/gemILF/gemILF_PS_ePA/gemILF_PS_ePA_V3.2.3/#A_20667-02
    return oidcClient.fetchJwk(encKeyUri);
  }

  private JWEObject nestAsJwe(@NonNull JOSEObject nested, @NonNull Instant exp) {

    var alg = JWEAlgorithm.ECDH_ES;
    var enc = EncryptionMethod.A256GCM;
    // https://datatracker.ietf.org/doc/html/draft-yusef-oauth-nested-jwt-03
    var jweHeader =
        new JWEHeader.Builder(alg, enc)
            .contentType("NJWT")
            .customParam("exp", exp.getEpochSecond())
            .build();
    var jweBody = new Payload(Map.of("njwt", nested.serialize()));
    return new JWEObject(jweHeader, jweBody);
  }

  private void debugLogChallengeJwe(JWEObject jwe) {
    if (!log.isDebugEnabled()) {
      return;
    }
    log.atDebug()
        // header was updated with ephemeral key
        .addKeyValue("header", jwe.getHeader().toString())
        .addKeyValue("payload", jwe.getPayload().toString())
        .log("encrypting nested challenge");
  }

  private Instant expiryFromChallengeBody(SignedJWT challenge) {

    try {
      var challengeClaims = challenge.getJWTClaimsSet();
      if (challengeClaims == null) {
        throw new AuthorizationException("empty challenge claims");
      }
      var challengeExp = challengeClaims.getExpirationTime();
      if (challengeExp == null) {
        throw new AuthorizationException("challenge without expiry");
      }
      return challengeExp.toInstant();
    } catch (ParseException e) {
      throw new AuthorizationException("failed to parse challenge expiry claim", e);
    }
  }

  private SignedJWT signChallenge(String challenge) {
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Dienst/gemSpec_IDP_Dienst_V1.7.0/#7.3
    try {
      var claims = new JWTClaimsSet.Builder().claim("njwt", challenge).build();

      var cert = eccSignatureService.authCertificate();

      var header =
          // Using ECC with Brainpool curve
          new JWSHeader.Builder(BrainpoolAlgorithms.BS256R1)
              .type(JOSEObjectType.JWT)
              .x509CertChain(List.of(Base64.encode(cert.getEncoded())))
              .contentType("NJWT")
              .build();

      var jwt = new SignedJWT(header, claims);

      var signer = new SmcBEccSigner(eccSignatureService);
      jwt.sign(signer);

      debugLogSignedChallenge(challenge, jwt);

      return jwt;
    } catch (JOSEException | ParseException | CertificateEncodingException e) {
      throw new AuthorizationException("failed to sign challenge", e);
    }
  }

  private void debugLogSignedChallenge(String challenge, SignedJWT jwt) throws ParseException {
    if (!log.isDebugEnabled()) {
      return;
    }

    var principal = eccSignatureService.authCertificate().getSubjectX500Principal().getName();
    var header = jwt.getHeader().toString();
    var payload = JSONObjectUtils.toJSONString(jwt.getJWTClaimsSet().toJSONObject());
    log.atDebug()
        .addKeyValue("principal", principal)
        .addKeyValue("challenge", challenge)
        .addKeyValue("header", header)
        .addKeyValue("payload", payload)
        .addKeyValue("jwt", jwt.serialize())
        .log(
            "signed challenge\nprincipal: {}\nchallenge: {}\nheader\n===\n{}\n===\npayload\n===\n{}\n===\njwt\n===\n{}\n===\n",
            principal,
            challenge,
            header,
            payload,
            jwt.serialize());
  }

  private BP256ECKey fetchEncryptionKeyFromOidcConfig(URI issuer) {
    try {
      // Base URI for OIDC configuration
      String oidcConfigUrl = issuer.toString();
      if (!oidcConfigUrl.endsWith("/")) {
        oidcConfigUrl += "/";
      }
      oidcConfigUrl += ".well-known/openid-configuration";

      log.debug("Fetching OIDC configuration from: {}", oidcConfigUrl);

      // Fetch the OIDC configuration
      URL url = new URL(oidcConfigUrl);
      Map<String, Object> oidcConfig = JSONObjectUtils.parse(readUrlContent(url));

      // Get the JWKS URI from the configuration
      String jwksUri = (String) oidcConfig.get("jwks_uri");
      if (jwksUri == null) {
        throw new AuthorizationException("JWKS URI not found in OIDC configuration");
      }

      log.debug("Fetching JWKS from: {}", jwksUri);

      // Fetch the JWKS
      URL jwksUrl = new URL(jwksUri);
      Map<String, Object> jwks = JSONObjectUtils.parse(readUrlContent(jwksUrl));

      // Find the encryption key with kid "puk_idp_enc" and use "enc"
      List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
      for (Map<String, Object> key : keys) {
        String kid = (String) key.get("kid");
        String use = (String) key.get("use");
        String kty = (String) key.get("kty");

        if ("puk_idp_enc".equals(kid) && "enc".equals(use) && "EC".equals(kty)) {
          // Parse the key
          return BP256ECKey.parse(JSONObjectUtils.toJSONString(key));
        }
      }

      throw new AuthorizationException("Encryption key not found in JWKS");
    } catch (Exception e) {
      log.error("Failed to fetch encryption key from OIDC configuration", e);
      throw new AuthorizationException("Failed to fetch encryption key from OIDC configuration", e);
    }
  }

  private String readUrlContent(URL url) throws IOException {
    try (var reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
      StringBuilder content = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        content.append(line);
      }
      return content.toString();
    }
  }
}
