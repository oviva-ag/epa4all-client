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

package de.gematik.vau.lib;

import de.gematik.vau.lib.crypto.KEM;
import de.gematik.vau.lib.data.*;
import de.gematik.vau.lib.exceptions.VauEncryptionException;
import de.gematik.vau.lib.exceptions.VauProtocolException;
import de.gematik.vau.lib.util.ArrayUtils;
import de.gematik.vau.lib.util.DigestUtils;
import java.security.InvalidKeyException;
import java.util.Arrays;
import lombok.Getter;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** State machine for the VaU client. An instance of this class is created for each connection. */
@Getter
public class VauClientStateMachine extends AbstractVauStateMachine {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private EccKyberKeyPair clientKey1;
  private KdfKey1 kdfClientKey1;
  private KdfKey2 clientKey2;
  private byte[] transcriptClient = new byte[0];
  private long requestCounter = 0;

  private final SignedPublicKeysTrustValidator signedPublicKeysTrustValidator;

  public VauClientStateMachine(
      boolean isPu, SignedPublicKeysTrustValidator signedPublicKeysTrustValidator) {
    super(isPu);
    this.signedPublicKeysTrustValidator = signedPublicKeysTrustValidator;
  }

  /**
   * Handshake Message 1: Generates Key Pairs, stores them in a Message1 and encodes it
   *
   * @return the encoded message 1
   */
  public byte[] generateMessage1() {
    if (clientKey1 == null) {
      clientKey1 = EccKyberKeyPair.generateRandom();
    }

    var message1 = VauMessage1.fromClientKey(clientKey1);
    byte[] message1Encoded = encodeUsingCbor(message1);

    log.atDebug().log(() -> "Generated message1: " + Hex.toHexString(message1Encoded));
    transcriptClient = message1Encoded;
    return message1Encoded;
  }

  /**
   * Handshake Message 3: uses CBOR decoded Message 2; calculates the shared secrets and uses them
   * to aead decrypt the signed server PublicKeys; then it uses these keys to generate other shared
   * secrets, which are used to generate a second key, which will be used for en-/decryption of
   * messages after the handshake; creates Message 3 including the aead encrypted ciphertexts of the
   * shared secrets for the second key (kem certificates) and the aead encrypted client hash
   *
   * @param message2Encoded Message 2 with aead encrypted publicKey and the ECDH and Kyber
   *     ciphertexts
   * @return Message 3 with aead encrypted ciphertexts and client hash
   */
  public byte[] receiveMessage2(byte[] message2Encoded) {
    VauMessage2 vauMessage2;
    try {
      vauMessage2 = decodeCborMessageToClass(message2Encoded, VauMessage2.class);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Could not CBOR decode Message 2 when receiving it at client. " + e.getMessage(), e);
    }

    var clientKemResult1 = KEM.decapsulateMessages(vauMessage2, clientKey1);
    kdfClientKey1 = KEM.kdf(clientKemResult1);
    byte[] transferredSignedServerPublicKey =
        KEM.decryptAead(kdfClientKey1.serverToClient(), vauMessage2.aeadCt());

    SignedPublicVauKeys signedPublicVauKeys;
    try {
      signedPublicVauKeys =
          decodeCborMessageToClass(transferredSignedServerPublicKey, SignedPublicVauKeys.class);
    } catch (Exception e) {
      throw new VauProtocolException(
          "Could not CBOR decode Signed Server Public Keys when receiving it at client.", e);
    }

    log.atDebug().log("VAU signed public keys: {}", signedPublicVauKeys);
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/latest/#A_24425-01
    if (!signedPublicKeysTrustValidator.isTrusted(
        new SignedPublicKeysTrustValidator.SignedPublicKeys(
            signedPublicVauKeys.signedPubKeys(),
            signedPublicVauKeys.signatureEs256(),
            signedPublicVauKeys.certHash(),
            signedPublicVauKeys.cdv(),
            signedPublicVauKeys.ocspResponse()))) {
      throw new VauProtocolException(
          "Failed to establish trust in VAU public keys: %s".formatted(signedPublicVauKeys));
    }

    var transferredSignedServerPublicKeyList = signedPublicVauKeys.extractVauKeys();
    log.atDebug().log("VAU public keys: {}", transferredSignedServerPublicKeyList);

    checkCertificateExpired(transferredSignedServerPublicKeyList.exp());

    verifyClientMessageIsWellFormed(
        transferredSignedServerPublicKeyList.ecdhPublicKey(), transferredSignedServerPublicKeyList);
    var clientKemResult2 =
        KEM.encapsulateMessage(
            transferredSignedServerPublicKeyList.ecdhPublicKey().toEcPublicKey(),
            transferredSignedServerPublicKeyList.kyberPublicKey());

    var innerLayer =
        VauMessage3InnerLayer.builder()
            .kyberCt(clientKemResult2.kyberCt())
            .ecdhCt(clientKemResult2.ecdhCt())
            .erp(false)
            .eso(false)
            .build();

    byte[] message3InnerLayerEncoded = encodeUsingCbor(innerLayer);
    byte[] aeadCipherTextMessage3 =
        KEM.encryptAead(kdfClientKey1.clientToServer(), message3InnerLayerEncoded);

    transcriptClient = ArrayUtils.addAll(transcriptClient, message2Encoded);
    byte[] transcriptClientToSend = ArrayUtils.addAll(transcriptClient, aeadCipherTextMessage3);

    clientKey2 = KEM.kdf(clientKemResult1, clientKemResult2);
    setEncryptionVauKey(new EncryptionVauKey(clientKey2.clientToServerAppData()));
    setDecryptionVauKey(clientKey2.serverToClientAppData());
    setKeyId(clientKey2.keyId());
    byte[] transcriptClientHash = DigestUtils.sha256(transcriptClientToSend);

    byte[] aeadCiphertextMessage3KeyKonfirmation =
        KEM.encryptAead(clientKey2.clientToServerKeyConfirmation(), transcriptClientHash);
    VauMessage3 message3 =
        new VauMessage3("M3", aeadCipherTextMessage3, aeadCiphertextMessage3KeyKonfirmation);
    byte[] message3Encoded = encodeUsingCbor(message3);
    transcriptClient = ArrayUtils.addAll(transcriptClient, message3Encoded);
    return message3Encoded;
  }

  /**
   * receives server hash and verifies the handshake by comparing it to its own
   *
   * @param message4Encoded CBOR decoded Message 4, containing the aead encrypted server hash
   */
  public void receiveMessage4(byte[] message4Encoded) {
    VauMessage4 message4;
    try {
      message4 = decodeCborMessageToClass(message4Encoded, VauMessage4.class);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Could not CBOR decode Message 4 when receiving it at client. " + e.getMessage(), e);
    }
    byte[] vauTranscript =
        KEM.decryptAead(
            clientKey2.serverToClientKeyConfirmation(), message4.getAeadCtKeyKonfirmation());
    byte[] newTranscriptClientHash = DigestUtils.sha256(transcriptClient);

    if (!Arrays.equals(vauTranscript, newTranscriptClientHash)) {
      var cause =
          new InvalidKeyException("Vau transcript and new client transcript hash do not equal.");
      throw new VauProtocolException("bad message 4", cause);
    }
  }

  @Override
  public byte[] encryptVauMessage(byte[] cleartext) {
    try {
      requestCounter++;
      return super.encryptVauMessage(cleartext);
    } catch (IllegalArgumentException | VauEncryptionException e) {
      throw new VauEncryptionException(
          "Exception thrown whilst trying to encrypt VAU message. ", e);
    }
  }

  @Override
  public byte getRequestByte() {
    return 1;
  }

  @Override
  protected void checkRequestCounter(long reqCtr) {
    if (reqCtr != getRequestCounter()) {
      throw new IllegalArgumentException(
          "Invalid request counter. Expected " + (getRequestCounter() + 1) + ", got " + reqCtr);
    }
  }

  @Override
  protected void checkRequestByte(byte reqByte) {
    if (reqByte != 2) {
      throw new UnsupportedOperationException(
          "Request byte was unexpected. Expected 2, but got " + reqByte);
    }
  }

  @Override
  protected void checkRequestKeyId(byte[] keyId) {
    if (!Arrays.equals(clientKey2.keyId(), keyId)) {
      throw new IllegalArgumentException(
          "Key ID in the header "
              + Hex.toHexString(keyId)
              + " does not equals "
              + Hex.toHexString(clientKey2.keyId())
              + " stored on client side");
    }
  }

  private void verifyClientMessageIsWellFormed(
      VauEccPublicKey eccPublicKey, VauPublicKeys kyberPublicKey) {
    verifyEccPublicKey(eccPublicKey);

    // indirectly checks whether we can read the public key
    kyberPublicKey.kyberPublicKey();
  }
}
