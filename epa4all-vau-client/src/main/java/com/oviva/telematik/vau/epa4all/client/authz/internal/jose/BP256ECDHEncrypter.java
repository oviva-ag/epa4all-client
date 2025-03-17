/*
 * nimbus-jose-jwt
 *
 * Copyright 2012-2019, Connect2id Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.impl.AAD;
import com.nimbusds.jose.crypto.impl.ECDH;
import com.nimbusds.jose.crypto.impl.ECDHCryptoProvider;
import com.nimbusds.jose.jwk.Curve;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.Arrays;
import java.util.Set;
import javax.crypto.SecretKey;

/**
 * Elliptic Curve Diffie-Hellman encrypter of {@link com.nimbusds.jose.JWEObject JWE objects} for
 * curves using EC JWK keys. Expects a public EC key (with a BP-256 curve).
 *
 * <p>See RFC 7518 <a href="https://tools.ietf.org/html/rfc7518#section-4.6">section 4.6</a> for
 * more information.
 *
 * <p>This class is thread-safe.
 *
 * <p>Supports the following key management algorithms:
 *
 * <ul>
 *   <li>{@link com.nimbusds.jose.JWEAlgorithm#ECDH_ES}
 *   <li>{@link com.nimbusds.jose.JWEAlgorithm#ECDH_ES_A128KW}
 *   <li>{@link com.nimbusds.jose.JWEAlgorithm#ECDH_ES_A192KW}
 *   <li>{@link com.nimbusds.jose.JWEAlgorithm#ECDH_ES_A256KW}
 * </ul>
 *
 * <p>Supports the following elliptic curves:
 *
 * <ul>
 *   <li>{@link com.nimbusds.jose.jwk.Curve#P_256}
 *   <li>{@link com.nimbusds.jose.jwk.Curve#P_384}
 *   <li>{@link com.nimbusds.jose.jwk.Curve#P_521}
 * </ul>
 *
 * <p>Supports the following content encryption algorithms:
 *
 * <ul>
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#A128CBC_HS256}
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#A192CBC_HS384}
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#A256CBC_HS512}
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#A128GCM}
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#A192GCM}
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#A256GCM}
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#A128CBC_HS256_DEPRECATED}
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#A256CBC_HS512_DEPRECATED}
 *   <li>{@link com.nimbusds.jose.EncryptionMethod#XC20P}
 * </ul>
 *
 * @author Tim McLean
 * @author Vladimir Dzhuvinov
 * @author Fernando González Callejas
 * @author Egor Puzanov
 * @author Thomas Richner
 * @version 2024-11-26
 */
public class BP256ECDHEncrypter extends ECDHCryptoProvider implements JWEEncrypter {

  /** The supported EC JWK curves by the ECDH crypto provider class. */
  public static final Set<Curve> SUPPORTED_ELLIPTIC_CURVES = Set.of(BrainpoolCurve.BP_256);

  /** The public EC key. */
  private final ECPublicKey publicKey;

  /**
   * Creates a new Elliptic Curve Diffie-Hellman encrypter.
   *
   * @param publicKey The public EC key. Must not be {@code null}.
   * @throws JOSEException If the elliptic curve is not supported.
   */
  public BP256ECDHEncrypter(final ECPublicKey publicKey) throws JOSEException {
    super(BrainpoolCurve.BP_256, null);
    this.publicKey = publicKey;
  }

  @Override
  public Set<Curve> supportedEllipticCurves() {

    return SUPPORTED_ELLIPTIC_CURVES;
  }

  @Override
  public JWECryptoParts encrypt(final JWEHeader header, final byte[] clearText, final byte[] aad)
      throws JOSEException {

    // Generate ephemeral EC key pair on the same curve as the consumer's public key
    KeyPair ephemeralKeyPair = generateEphemeralKeyPair(publicKey.getParams());
    ECPublicKey ephemeralPublicKey = (ECPublicKey) ephemeralKeyPair.getPublic();
    ECPrivateKey ephemeralPrivateKey = (ECPrivateKey) ephemeralKeyPair.getPrivate();

    // Add the ephemeral public EC key to the header
    var updatedHeader =
        new JWEHeader.Builder(header)
            .ephemeralPublicKey(BP256ECKey.fromPublicKey(ephemeralPublicKey))
            .build();

    // Derive 'Z'
    SecretKey Z =
        ECDH.deriveSharedSecret(
            publicKey, ephemeralPrivateKey, getJCAContext().getKeyEncryptionProvider());

    // for JWEObject we need update the AAD as well
    final byte[] updatedAAD =
        Arrays.equals(AAD.compute(header), aad) ? AAD.compute(updatedHeader) : aad;

    return encryptWithZ(updatedHeader, Z, clearText, updatedAAD);
  }

  /**
   * Generates a new ephemeral EC key pair with the specified curve.
   *
   * @param ecParameterSpec The EC key spec. Must not be {@code null}.
   * @return The EC key pair.
   * @throws JOSEException If the EC key pair couldn't be generated.
   */
  private KeyPair generateEphemeralKeyPair(final ECParameterSpec ecParameterSpec)
      throws JOSEException {

    Provider keProvider = getJCAContext().getKeyEncryptionProvider();

    try {
      KeyPairGenerator generator;

      if (keProvider != null) {
        generator = KeyPairGenerator.getInstance("EC", keProvider);
      } else {
        generator = KeyPairGenerator.getInstance("EC");
      }

      generator.initialize(ecParameterSpec);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
      throw new JOSEException("Couldn't generate ephemeral EC key pair: " + e.getMessage(), e);
    }

    //    Provider keProvider = getJCAContext().getKeyEncryptionProvider();

    //    keProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);

    //    try {
    //
    //      var ecSpec = ECNamedCurveTable.getParameterSpec("brainpoolP256r1");
    //
    //      var generator = KeyPairGenerator.getInstance("EC", keProvider);
    //      generator.initialize(ecParameterSpec);
    //
    //      return generator.generateKeyPair();
    //    } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
    //      throw new JOSEException("Couldn't generate ephemeral EC key pair: " + e.getMessage(),
    // e);
    //    }
  }
}
