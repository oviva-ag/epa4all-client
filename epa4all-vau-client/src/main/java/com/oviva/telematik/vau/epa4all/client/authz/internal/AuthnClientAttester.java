package com.oviva.telematik.vau.epa4all.client.authz.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import com.oviva.telematik.vau.epa4all.client.authz.SignatureService;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class AuthnClientAttester {

  private final SignatureService signatureService;

  public AuthnClientAttester(SignatureService signatureService) {
    this.signatureService = signatureService;
  }

  public SignedJWT attestClient(String nonce) {
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Aktensystem_ePAfueralle/gemSpec_Aktensystem_ePAfueralle_V1.2.0/#A_25444-01

    var iat = Instant.now();

    // A_25444-01
    var exp = iat.plus(Duration.ofMinutes(20));

    var claims =
        new JWTClaimsSet.Builder()
            .issueTime(Date.from(iat))
            .expirationTime(Date.from(exp))
            .claim("nonce", nonce)
            .build();

    var cert = signatureService.authCertificate();

    try {
      var x5c = Base64.encode(cert.getEncoded());

      // TODO: We can also sign with the brainpoolP256r curve and pretend its ES256
      // this is not according to the official RFC, this is the intended way for this use-case
      // though ¯\_(ツ)_/¯

      var header =
          new JWSHeader.Builder(JWSAlgorithm.ES256)
              .type(JOSEObjectType.JWT)
              .x509CertChain(List.of(x5c))
              .build();

      var jwt = new SignedJWT(header, claims);

      jwt.sign(new SmcBSigner(signatureService));
      return jwt;
    } catch (JOSEException | CertificateEncodingException e) {
      throw new AuthorizationException("failed client attestation - signing nonce", e);
    }
  }
}
