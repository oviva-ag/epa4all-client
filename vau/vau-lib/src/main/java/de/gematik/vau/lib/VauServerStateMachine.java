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
import de.gematik.vau.lib.exceptions.VauProtocolException;
import de.gematik.vau.lib.util.ArrayUtils;
import de.gematik.vau.lib.util.DigestUtils;
import java.io.IOException;
import java.util.Arrays;
import lombok.Getter;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State machine for the VauServer. An instance of this class is created for each connection. It
 * handles the handshake phase and can then be used to send and receive encrytped messages.
 */
@Getter
public class VauServerStateMachine extends AbstractVauStateMachine {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private final SignedPublicVauKeys signedPublicVauKeys;
  private final EccKyberKeyPair serverVauKeys;
  private byte[] c2s; // S_K1_c2s
  private byte[] s2c; // S_K1_s2c
  private KdfMessage kemResult1;
  private KdfMessage kemResult2;
  private byte[] serverTranscript;
  private KdfKey2 serverKey2;
  private long clientRequestCounter;
  private static final int EXPIRATION_DAYS = 30;

  public VauServerStateMachine(
      SignedPublicVauKeys vauKeys, EccKyberKeyPair kyberKeys, boolean isPu) {
    super(isPu);

    int iat = vauKeys.extractVauKeys().iat();
    int exp = vauKeys.extractVauKeys().exp();
    if (exp - iat > EXPIRATION_DAYS * 60 * 60 * 24) {
      throw new IllegalArgumentException(
          "Dates of initialization and expiration of server keys can be only up to 30 days apart.");
    }

    this.signedPublicVauKeys = vauKeys;
    this.serverVauKeys = kyberKeys;
  }

  public VauServerStateMachine(
      SignedPublicVauKeys signedPublicVauKeys, EccKyberKeyPair serverVauKeys) {
    this(signedPublicVauKeys, serverVauKeys, false);
  }

  /**
   * Uses decoded Message to create Handshake Message 2 or 4
   *
   * @param encodedMessage CBOR decoded Message 1 or 3
   * @return CBOR decoded Message 2 or 4
   */
  public byte[] receiveMessage(byte[] encodedMessage) {
    checkCertificateExpired(signedPublicVauKeys.extractVauKeys().exp());

    try {
      Object message = decodeCborMessageToClass(encodedMessage);
      if (message instanceof VauMessage1 message1) {
        return receiveMessage1(message1, encodedMessage);
      } else if (message instanceof VauMessage3 message3) {
        return receiveMessage3(message3, encodedMessage);
      } else {
        throw new UnsupportedOperationException("Message type not supported");
      }
    } catch (IOException e) {
      throw new VauProtocolException("failed to pares CBOR message", e);
    }
  }

  /**
   * Handshake Message 2: takes public keys from Message 1 and calculates shared secrets in order to
   * generate server-to-client and client-to-server keys; creates Message 2 with aead encrypted
   * public key and the ciphertexts, which are generated using the client PublicKeys
   *
   * @param vauMessage1 Message 1 from Client with the remote PublicKeys
   * @param message1Encoded CBOR encoded Message 1
   * @return Message 2 with aead encrypted publicKey and the ciphertexts
   */
  private byte[] receiveMessage1(VauMessage1 vauMessage1, byte[] message1Encoded) {
    serverTranscript = message1Encoded;
    verifyClientMessageIsWellFormed(vauMessage1);

    kemResult1 =
        KEM.encapsulateMessage(
            vauMessage1.ecdhPublicKey().toEcPublicKey(), vauMessage1.kyberPublicKey());
    if (log.isTraceEnabled()) {
      log.trace("ecdh_shared_secret: (hexdump) {}", Hex.toHexString(kemResult1.ecdhSharedSecret()));
      log.trace(
          "Kyber768_shared_secret: (hexdump) {}", Hex.toHexString(kemResult1.kyberSharedSecret()));
    }
    KdfKey1 kdfServerKey1 = KEM.kdf(kemResult1);
    c2s = kdfServerKey1.clientToServer();
    s2c = kdfServerKey1.serverToClient();

    byte[] encodedSignedPublicVauKeys = encodeUsingCbor(signedPublicVauKeys);
    byte[] aeadCiphertextMessage2 =
        KEM.encryptAead(kdfServerKey1.serverToClient(), encodedSignedPublicVauKeys);
    VauMessage2 message2 =
        VauMessage2.create(kemResult1.ecdhCt(), kemResult1.kyberCt(), aeadCiphertextMessage2);
    log.atDebug().log(() -> "Generated message1: " + Hex.toHexString(message1Encoded));
    byte[] message2Encoded = encodeUsingCbor(message2);
    serverTranscript = ArrayUtils.addAll(serverTranscript, message2Encoded);
    return message2Encoded;
  }

  /**
   * Handshake Message 4: uses Message3 from Client; aead decrypts the kem certificates; these are
   * then used to create the same KdfKey2 as the client did when receiving message 2. In order to
   * verify that both keys are identical, the client hash is deciphered with the newly generated key
   * and compared with the hashed server transcript. The aead encrypted server hash is then returned
   * in Message 4
   *
   * @param vauMessage3 Message 3 from client, containing aead encrypted kem certificates and client
   *     hash
   * @param message3Encoded Message 3 CBOR encoded
   * @return CBOR decoded Message 4 containing the aead encrypted server hash
   */
  private byte[] receiveMessage3(VauMessage3 vauMessage3, byte[] message3Encoded) {
    byte[] transcriptServerToCheck = ArrayUtils.addAll(serverTranscript, vauMessage3.aeadCt());
    serverTranscript = ArrayUtils.addAll(serverTranscript, message3Encoded);

    byte[] kemCertificatesEncoded = KEM.decryptAead(c2s, vauMessage3.aeadCt());

    VauMessage3InnerLayer kemCertificates;
    try {
      kemCertificates =
          decodeCborMessageToClass(kemCertificatesEncoded, VauMessage3InnerLayer.class);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Could not CBOR decode KEM certificates (inner layer of message 3) when receiving it at client. "
              + e.getMessage(),
          e);
    }

    kemResult2 = KEM.decapsulateMessages(kemCertificates, serverVauKeys);
    serverKey2 = KEM.kdf(kemResult1, kemResult2);
    setEncryptionVauKey(new EncryptionVauKey(serverKey2.serverToClientAppData()));
    setDecryptionVauKey(serverKey2.clientToServerAppData());
    setKeyId(serverKey2.keyId());
    byte[] clientTranscriptHash =
        KEM.decryptAead(
            serverKey2.clientToServerKeyConfirmation(), vauMessage3.aeadCtKeyKonfirmation());

    byte[] clientVauHashCalculation = DigestUtils.sha256(transcriptServerToCheck);

    if (!Arrays.equals(clientTranscriptHash, clientVauHashCalculation)) {
      throw new VauProtocolException("Client transcript hash and vau calculation do not equal.");
    }
    byte[] transcriptServerHash = DigestUtils.sha256(serverTranscript);
    byte[] aeadCiphertextMessage4KeyKonfirmation =
        KEM.encryptAead(serverKey2.serverToClientKeyConfirmation(), transcriptServerHash);
    VauMessage4 message4 = new VauMessage4("M4", aeadCiphertextMessage4KeyKonfirmation);
    return encodeUsingCbor(message4);
  }

  @Override
  protected long getRequestCounter() {
    return clientRequestCounter;
  }

  @Override
  public byte getRequestByte() {
    return 2;
  }

  @Override
  protected void checkRequestCounter(long reqCtr) {
    this.clientRequestCounter = reqCtr;
  }

  @Override
  protected void checkRequestByte(byte reqByte) {
    if (reqByte != 1) {
      throw new UnsupportedOperationException(
          "Request byte was unexpected. Expected 1, but got " + reqByte);
    }
  }

  @Override
  protected void checkRequestKeyId(byte[] keyId) {
    if (!Arrays.equals(serverKey2.keyId(), keyId)) {
      throw new IllegalArgumentException(
          "Key ID in the header "
              + Hex.toHexString(keyId)
              + " does not equals "
              + Hex.toHexString(serverKey2.keyId())
              + " stored on server side");
    }
  }

  private void verifyClientMessageIsWellFormed(VauMessage1 vauMessage1) {
    verifyEccPublicKey(vauMessage1.ecdhPublicKey());

    // indirectly verifies we can read the key
    vauMessage1.kyberPublicKey();
  }
}
