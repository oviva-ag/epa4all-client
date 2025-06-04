/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.vau.lib.data;

import static de.gematik.vau.lib.crypto.KyberKeys.KYBER_PRIVATE_KEY_ENCODING_HEADER;
import static de.gematik.vau.lib.crypto.KyberKeys.KYBER_PUBLIC_KEY_ENCODING_HEADER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.gematik.vau.lib.crypto.EllipticCurve;
import de.gematik.vau.lib.crypto.KyberEncoding;
import de.gematik.vau.lib.util.ArrayUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

public class EccKyberKeyPair {

  private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

  private final KeyPair eccKeyPair;
  private final KeyPair kyberKeyPair;

  public EccKyberKeyPair(KeyPair eccKeyPair, KeyPair kyberKeyPair) {
    this.eccKeyPair = eccKeyPair;
    this.kyberKeyPair = kyberKeyPair;
  }

  public KeyPair eccKeyPair() {
    return eccKeyPair;
  }

  public KeyPair kyberKeyPair() {
    return kyberKeyPair;
  }

  /**
   * Generates a random ECDH key pair and a random Kyber-768 key pair
   *
   * @return EccKyberKeyPair, which is the object containing both key pairs
   */
  public static EccKyberKeyPair generateRandom() {
    KeyPair ecdhKeyPair = EllipticCurve.generateKeyPair();
    KeyPair kybKeyPair = KyberEncoding.generateKeyPair();

    return new EccKyberKeyPair(ecdhKeyPair, kybKeyPair);
  }

  public static EccKyberKeyPair readFromFile(Path file) {
    try {
      final CBORMapper cborMapper = new CBORMapper();
      final JsonNode tree = cborMapper.readTree(Files.readAllBytes(file));

      final byte[] eccPrivateKeyData = tree.get("ECDH_PrivKey").binaryValue();
      final byte[] kyberPublicKeyData = tree.get("Kyber768_PK").binaryValue();
      final byte[] kyberPrivateKeyData = tree.get("Kyber768_PrivKey").binaryValue();
      var eccKeyPair = readEcdsaKeypairFromPkcs8Pem(eccPrivateKeyData);
      var kyberKeyPair = readKyberKeypairFromPkcs8Pem(kyberPrivateKeyData, kyberPublicKeyData);
      return new EccKyberKeyPair(eccKeyPair, kyberKeyPair);
    } catch (IOException e) {
      throw new IllegalArgumentException("cannot read file %s".formatted(file), e);
    }
  }

  private static KeyPair readKyberKeypairFromPkcs8Pem(
      byte[] kyberPrivateKeyData, byte[] kyberPublicKeyData) {
    try {
      final String keyType = "KYBER";
      KeyFactory keyFactory = KeyFactory.getInstance(keyType, new BouncyCastlePQCProvider());

      X509EncodedKeySpec kyberPubKey =
          new X509EncodedKeySpec(
              ArrayUtils.unionByteArrays(KYBER_PUBLIC_KEY_ENCODING_HEADER, kyberPublicKeyData),
              keyType);
      PKCS8EncodedKeySpec kyberPrivKey =
          new PKCS8EncodedKeySpec(
              ArrayUtils.unionByteArrays(KYBER_PRIVATE_KEY_ENCODING_HEADER, kyberPrivateKeyData),
              keyType);

      return new KeyPair(
          keyFactory.generatePublic(kyberPubKey), keyFactory.generatePrivate(kyberPrivKey));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalArgumentException("failed to read PEM kyber key", e);
    }
  }

  public static KeyPair readEcdsaKeypairFromPkcs8Pem(byte[] eccPrivateKeyData) {
    try {
      var factory = KeyFactory.getInstance("ECDSA", BOUNCY_CASTLE_PROVIDER);
      var privKeySpec = new PKCS8EncodedKeySpec(eccPrivateKeyData);
      final var privateKey = (BCECPrivateKey) factory.generatePrivate(privKeySpec);
      var keyFactory = KeyFactory.getInstance("ECDSA", BOUNCY_CASTLE_PROVIDER);

      var ecSpec = privateKey.getParameters();
      var q = ecSpec.getG().multiply(privateKey.getD());

      var pubSpec = new ECPublicKeySpec(q, ecSpec);
      return new KeyPair(keyFactory.generatePublic(pubSpec), privateKey);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalArgumentException("failed to read PEM ECDSA key", e);
    }
  }
}
