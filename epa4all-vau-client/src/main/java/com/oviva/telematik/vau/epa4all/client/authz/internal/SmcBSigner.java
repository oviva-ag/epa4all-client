package com.oviva.telematik.vau.epa4all.client.authz.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import com.oviva.telematik.vau.epa4all.client.authz.RsaSignatureService;
import java.util.Set;
import java.util.stream.Collectors;

public class SmcBSigner implements JWSSigner {

  private final RsaSignatureService signer;

  public SmcBSigner(RsaSignatureService signer) {
    this.signer = signer;
  }

  @Override
  public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {

    var alg = header.getAlgorithm();
    var supported = supportedJWSAlgorithms();
    if (!supported.contains(alg)) {
      throw new JOSEException(
          "unsupported alg '%s', supported: %s"
              .formatted(
                  alg,
                  supported.stream().map(JWSAlgorithm::getName).collect(Collectors.joining(" "))));
    }

    var signed = signer.authSign(signingInput);
    return Base64URL.encode(signed);
  }

  @Override
  public Set<JWSAlgorithm> supportedJWSAlgorithms() {
    // TODO: We can also advertise and support elliptic-curve algorithms, though only with brainpool
    // curves while advertising ES256 (non-standard).
    return Set.of(JWSAlgorithm.PS256);
  }

  @Override
  public JCAContext getJCAContext() {
    throw new UnsupportedOperationException();
  }
}
