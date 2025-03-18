package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class BrainpoolKeyGenerator {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  public static KeyPair generateBP256KeyPair() {
    try {
      KeyPairGenerator keyPairGen =
          KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
      ECGenParameterSpec paramSpec = new ECGenParameterSpec("brainpoolP256r1");
      keyPairGen.initialize(paramSpec);
      return keyPairGen.generateKeyPair();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate Brainpool P-256 key pair", e);
    }
  }
}
