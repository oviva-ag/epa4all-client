package com.oviva.telematik.vau.epa4all.client.authz.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolJwsVerifier;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.CertificateUtil;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OidcDiscoveryValidatorImpl implements OidcClient.DiscoveryValidator {

  // https://gemspec.gematik.de/docs/gemSpec/gemSpec_OID/gemSpec_OID_V3.23.0/#GS-A_4446-17
  private static final ASN1ObjectIdentifier OID_IDPD =
      new ASN1ObjectIdentifier("1.2.276.0.76.4.260");

  private static final Logger logger = LoggerFactory.getLogger(OidcDiscoveryValidatorImpl.class);

  private final KeyStore trustStore;

  public OidcDiscoveryValidatorImpl(KeyStore trustStore) {
    this.trustStore = trustStore;
  }

  @Override
  public void validate(SignedJWT jwt) {

    verifySignature(jwt);
    verifyClaims(jwt);
  }

  private void verifySignature(SignedJWT jwt) {

    var cert = signingCertificcate(jwt);
    verifyTrustChainAgainstRoot(cert);
    verifyRole(cert);
    verifySignature(jwt, cert);
  }

  private X509Certificate signingCertificcate(SignedJWT jwt) {

    var x5cRaw =
        Optional.ofNullable(jwt.getHeader())
            .map(h -> h.getX509CertChain())
            .orElse(List.of())
            .stream()
            // the first one MUST be the one used for signing
            // https://datatracker.ietf.org/doc/html/rfc7515#section-4.1.6
            .findFirst()
            .orElseThrow(
                () ->
                    new AuthorizationException(
                        "OIDC discovery document 'x5c' header claim is missing"));
    try {
      return CertificateUtil.parseDer(x5cRaw.decode());
    } catch (CertificateException e) {
      throw new AuthorizationException(
          "Failed to parse OIDC discovery document x509 header claim: " + e.getMessage(), e);
    }
  }

  private void verifyTrustChainAgainstRoot(X509Certificate endUserCertificate) {

    try {

      var target = new X509CertSelector();
      target.setCertificate(endUserCertificate);

      var params = new PKIXBuilderParameters(trustStore, target);

      // there are no CRLs to be found
      params.setRevocationEnabled(false);

      var builder = CertPathBuilder.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME);

      var result = (PKIXCertPathBuilderResult) builder.build(params);
      logger.atDebug().log(
          "certificate '{}' verified with trust anchor: '{}'",
          endUserCertificate.getSubjectX500Principal().getName(),
          result.getTrustAnchor().getTrustedCert().getSubjectX500Principal().getName());

    } catch (CertPathBuilderException
        | NoSuchAlgorithmException
        | InvalidAlgorithmParameterException e) {
      var name = endUserCertificate.getSubjectX500Principal().getName();
      throw new AuthorizationException(
          "failed to validate IDP discovery document signing certificate, bad certificate: " + name,
          e);
    } catch (NoSuchProviderException | KeyStoreException e) {
      throw new AuthorizationException("unexpected crypto exception", e);
    }
  }

  private void verifyRole(X509Certificate trustedSigningCertificate) {
    var oid =
        CertificateUtil.getProfessionOid(trustedSigningCertificate)
            .orElseThrow(
                () -> new AuthorizationException("missing profession OID for IDP certificate"));
    if (!oid.equals(OID_IDPD)) {
      throw new AuthorizationException("expected OID %s, got %s".formatted(OID_IDPD, oid));
    }
  }

  private void verifySignature(SignedJWT jwt, X509Certificate trustedSigningCertificate) {
    var verifier = new BrainpoolJwsVerifier((ECPublicKey) trustedSigningCertificate.getPublicKey());
    try {
      if (!verifier.verify(jwt.getHeader(), jwt.getSigningInput(), jwt.getSignature())) {
        throw new AuthorizationException("bad signature");
      }
    } catch (JOSEException e) {
      throw new AuthorizationException("failed to verify signature", e);
    }
  }

  private void verifyClaims(SignedJWT jwt) {

    var claims = parseClaims(jwt);
    var claimsVerifier =
        new DefaultJWTClaimsVerifier<>(
            null, Set.of("iat", "exp", "issuer", "uri_puk_idp_enc", "uri_puk_idp_sig", "jwks_uri"));
    try {
      claimsVerifier.verify(claims, null);
    } catch (BadJWTException e) {
      throw new AuthorizationException("Bad discovery document", e);
    }
  }

  private JWTClaimsSet parseClaims(SignedJWT jwt) {
    try {
      return jwt.getJWTClaimsSet();
    } catch (ParseException e) {
      throw new AuthorizationException("Failed to parse JWT claims", e);
    }
  }
}
