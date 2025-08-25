package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import com.oviva.telematik.vau.epa4all.client.authz.SignatureService;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BP256ECKey;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolAlgorithms;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthnChallengeResponderTest {

  @Mock private SignatureService signatureService;
  @Mock private OidcClient oidcClient;
  @Mock private X509Certificate mockCertificate;

  private AuthnChallengeResponder responder;

  @BeforeAll
  static void setupProvider() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @BeforeEach
  void setUp() throws Exception {
    responder = new AuthnChallengeResponder(signatureService, oidcClient);

    lenient().when(signatureService.authCertificate()).thenReturn(mockCertificate);
    lenient().when(mockCertificate.getEncoded()).thenReturn("cert".getBytes());
    lenient()
        .when(mockCertificate.getSubjectX500Principal())
        .thenReturn(new X500Principal("CN=Test"));
  }

  @Test
  void challengeResponse_happyPath_returnsEncryptedNestedJwe() throws Exception {
    // Given
    var iss = URI.create("https://idp.example.test");
    var discovery =
        new OidcClient.OidcDiscoveryResponse(
            iss,
            Instant.now(),
            Instant.now().plusSeconds(600),
            URI.create("https://jwks.example.test/puk_idp_enc"),
            URI.create("https://jwks.example.test/puk_idp_sig"),
            URI.create("https://jwks.example.test/jwks.json"));

    when(oidcClient.fetchOidcDiscoveryDocument(iss)).thenReturn(discovery);

    // brainpool key for both challenge verification (SIG) and encryption (ENC)
    var bpKeyPair = generateBrainpoolP256KeyPair();
    var bpPubJwk = BP256ECKey.fromPublicKey((ECPublicKey) bpKeyPair.getPublic());
    when(oidcClient.fetchJwk(discovery.uriPukIdpEnc())).thenReturn(bpPubJwk);
    when(oidcClient.fetchJwk(discovery.uriPukIdpSig())).thenReturn(bpPubJwk);

    // signature service returns non-empty signature bytes
    when(signatureService.authSign(any(byte[].class))).thenReturn("sig".getBytes());

    // challenge JWT signed with brainpool private key
    var challenge =
        createSignedChallengeJwt(
            iss.toString(), Instant.now().plusSeconds(300), (ECPrivateKey) bpKeyPair.getPrivate());

    // When
    var response = responder.challengeResponse(challenge.serialize());

    // Then
    assertEquals(iss, response.issuer());

    // JWE compact: 5 segments
    var parts = response.response().split("\\.");
    assertEquals(5, parts.length);

    // header (first segment)
    var headerJson = new String(Base64URL.from(parts[0]).decode());
    Map<String, Object> header = JSONObjectUtils.parse(headerJson);
    assertEquals("ECDH-ES", header.get("alg"));
    assertEquals("A256GCM", header.get("enc"));
    assertEquals("NJWT", header.get("cty"));
    assertTrue(header.containsKey("exp"));

    // payload contains nested signed JWT reference as second layer: ciphertext cannot be inspected,
    // but our NJWT is inside the encrypted payload per implementation
  }

  @Test
  void challengeResponse_throwsOnInvalidChallengeJwt() {
    // Given
    var invalid = "not-a-jwt";

    // When / Then
    assertThrows(AuthorizationException.class, () -> responder.challengeResponse(invalid));
  }

  @Test
  void encryptAndSignChallenge_throwsIfDiscoveryMissingEncKey() throws Exception {
    // Given
    var iss = URI.create("https://idp.example.test");
    var discovery =
        new OidcClient.OidcDiscoveryResponse(
            iss,
            Instant.now(),
            Instant.now().plusSeconds(600),
            null,
            URI.create("https://jwks.example.test/puk_idp_sig"),
            URI.create("https://jwks.example.test/jwks.json"));
    when(oidcClient.fetchOidcDiscoveryDocument(iss)).thenReturn(discovery);

    var kp = generateBrainpoolP256KeyPair();

    // signature service returns non-empty signature bytes to pass signing
    when(signatureService.authSign(any(byte[].class))).thenReturn("sig".getBytes());

    var challenge =
        createSignedChallengeJwt(
            iss.toString(), Instant.now().plusSeconds(120), (ECPrivateKey) kp.getPrivate());

    // When / Then
    assertThrows(
        AuthorizationException.class, () -> responder.encryptAndSignChallenge(iss, challenge));
  }

  private static KeyPair generateBrainpoolP256KeyPair() throws Exception {
    var kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
    kpg.initialize(new ECGenParameterSpec("brainpoolP256r1"));
    return kpg.generateKeyPair();
  }

  private static SignedJWT createSignedChallengeJwt(
      String issuer, Instant exp, ECPrivateKey privateKey) throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(exp))
            .claim("response_type", "code")
            .build();

    var header =
        new com.nimbusds.jose.JWSHeader.Builder(BrainpoolAlgorithms.BS256R1)
            .type(JOSEObjectType.JWT)
            .build();

    var jwt = new SignedJWT(header, claims);

    // compute JWS signature input
    var signingInput = jwt.getSigningInput();

    // sign using brainpool with BC provider
    var sig = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
    sig.initSign(privateKey);
    sig.update(signingInput);
    var derSignature = sig.sign();

    // convert DER to JOSE concatenated R||S (64 bytes for 256-bit curve)
    var jwsSignature = ECDSA.transcodeSignatureToConcat(derSignature, 64);

    var signed =
        new SignedJWT(
            jwt.getHeader().toBase64URL(),
            jwt.getPayload().toBase64URL(),
            Base64URL.encode(jwsSignature));
    return SignedJWT.parse(signed.serialize());
  }
}
