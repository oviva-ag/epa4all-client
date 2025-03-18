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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.gematik.vau.lib.exceptions.VauEncryptionException;
import de.gematik.vau.lib.exceptions.VauProtocolException;
import de.gematik.vau.lib.util.DigestUtils;
import java.io.IOException;
import java.security.*;
import java.util.HexFormat;

@SuppressWarnings("java:S6218")
public record SignedPublicVauKeys(
    @JsonProperty("signed_pub_keys") byte[] signedPubKeys,
    @JsonProperty("signature-ES256") byte[] signatureEs256,
    @JsonProperty("cert_hash") byte[] certHash,
    @JsonProperty("cdv") int cdv,
    @JsonProperty("ocsp_response") byte[] ocspResponse) {

  private static final CBORMapper CBOR_MAPPER = new CBORMapper();

  /**
   * Builds the SignedPublicVauKeys using the input
   *
   * @param serverAutCertificate decrypted server certificate in bytes
   * @param privateKey corresponding private key
   * @param ocspResponseAutCertificate decrypted OCSP response authorization certificate of client
   *     in bytes
   * @param cdv Cert-Data-Version (natural number, starting at 1)
   * @param vauServerKeys public keys of server
   * @return the SignedPublicVauKeys
   */
  public static SignedPublicVauKeys sign(
      byte[] serverAutCertificate,
      PrivateKey privateKey,
      byte[] ocspResponseAutCertificate,
      int cdv,
      VauPublicKeys vauServerKeys) {

    try {
      final byte[] keyBytes = CBOR_MAPPER.writeValueAsBytes(vauServerKeys);
      return SignedPublicVauKeys.builder()
          .signedPubKeys(keyBytes)
          .certHash(DigestUtils.sha256(serverAutCertificate))
          .cdv(cdv)
          .ocspResponse(ocspResponseAutCertificate)
          .signatureEs256(generateEccSignature(keyBytes, privateKey))
          .build();
    } catch (JsonProcessingException e) {
      throw new VauEncryptionException("failed to encode and sign VAU keys", e);
    }
  }

  private static byte[] generateEccSignature(byte[] tbsData, PrivateKey privateKey) {
    try {
      var ecdsaSignature = Signature.getInstance("SHA256withPLAIN-ECDSA", "BC");
      ecdsaSignature.initSign(privateKey);
      ecdsaSignature.update(tbsData);
      return ecdsaSignature.sign();
    } catch (NoSuchAlgorithmException
        | SignatureException
        | NoSuchProviderException
        | InvalidKeyException e) {
      throw new VauEncryptionException("Error while generating signature", e);
    }
  }

  public VauPublicKeys extractVauKeys() {
    try {
      return CBOR_MAPPER.readValue(signedPubKeys, VauPublicKeys.class);
    } catch (IllegalArgumentException | IOException e) {
      throw new VauProtocolException("Error while extracting VauKeys", e);
    }
  }

  @Override
  public String toString() {
    var hex = HexFormat.of();
    return "SignedPublicVauKeys[signedPubKeys='%s', signatureEs256='%s', certHash='%s', cdv=%s, ocspResponse='%s']"
        .formatted(
            hex.formatHex(signedPubKeys),
            hex.formatHex(signatureEs256),
            hex.formatHex(certHash),
            cdv,
            hex.formatHex(ocspResponse));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private byte[] signedPubKeys;
    private byte[] signatureEs256;
    private byte[] certHash;
    private int cdv;
    private byte[] ocspResponse;

    private Builder() {}

    public Builder signedPubKeys(byte[] signedPubKeys) {
      this.signedPubKeys = signedPubKeys;
      return this;
    }

    public Builder signatureEs256(byte[] signatureEs256) {
      this.signatureEs256 = signatureEs256;
      return this;
    }

    public Builder certHash(byte[] certHash) {
      this.certHash = certHash;
      return this;
    }

    public Builder cdv(int cdv) {
      this.cdv = cdv;
      return this;
    }

    public Builder ocspResponse(byte[] ocspResponse) {
      this.ocspResponse = ocspResponse;
      return this;
    }

    public SignedPublicVauKeys build() {
      return new SignedPublicVauKeys(signedPubKeys, signatureEs256, certHash, cdv, ocspResponse);
    }
  }
}
