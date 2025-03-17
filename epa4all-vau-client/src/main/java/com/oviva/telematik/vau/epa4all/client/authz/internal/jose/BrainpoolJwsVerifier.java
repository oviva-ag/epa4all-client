package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.impl.AlgorithmSupportMessage;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.proc.JWSVerifierFactory;
import com.nimbusds.jose.util.Base64URL;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class BrainpoolJwsVerifier implements JWSVerifier {

  private final ECPublicKey publicKey;

  public BrainpoolJwsVerifier(ECPublicKey publicKey) {
    this.publicKey = publicKey;
  }

  @Override
  public boolean verify(JWSHeader header, byte[] signingInput, Base64URL signature)
      throws JOSEException {
    final JWSAlgorithm alg = header.getAlgorithm();

    if (!supportedJWSAlgorithms().contains(alg)) {
      throw new JOSEException(
          AlgorithmSupportMessage.unsupportedJWSAlgorithm(alg, supportedJWSAlgorithms()));
    }

    final byte[] jwsSignature = signature.decode();

    final byte[] derSignature;
    try {
      derSignature = ECDSA.transcodeSignatureToDER(jwsSignature);
    } catch (JOSEException e) {
      // Invalid signature format
      return false;
    }

    try {
      var sig = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
      sig.initVerify(publicKey);
      sig.update(signingInput);
      return sig.verify(derSignature);

    } catch (InvalidKeyException e) {
      throw new JOSEException("Invalid EC public key: " + e.getMessage(), e);
    } catch (SignatureException e) {
      return false;
    } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
      throw new JOSEException("Unsupported EC signature algorithm: " + e.getMessage(), e);
    }
  }

  @Override
  public Set<JWSAlgorithm> supportedJWSAlgorithms() {
    return Set.of(BrainpoolAlgorithms.BS256R1);
  }

  @Override
  public JCAContext getJCAContext() {
    return null;
  }

  public static class Factory implements JWSVerifierFactory {

    @Override
    public JWSVerifier createJWSVerifier(JWSHeader header, Key key) throws JOSEException {
      return new BrainpoolJwsVerifier((ECPublicKey) key);
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
      return Set.of(BrainpoolAlgorithms.BS256R1);
    }

    @Override
    public JCAContext getJCAContext() {
      return null;
    }
  }
}
