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
import de.gematik.vau.lib.crypto.KyberKeys;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.bouncycastle.jce.interfaces.ECPublicKey;

@SuppressWarnings("java:S6218")
public record VauPublicKeys(
    @JsonProperty("iat") int iat,
    @JsonProperty("exp") int exp,
    @JsonProperty("comment") String comment,
    @JsonProperty("ECDH_PK") VauEccPublicKey ecdhPublicKey,
    @JsonProperty("Kyber768_PK") byte[] kyberPublicKeyBytes) {

  public static VauPublicKeys withValidity(
      EccKyberKeyPair eccKyberKeyPair, String comment, Duration validity) {
    var iat = (int) Instant.now().getEpochSecond();
    var exp = (int) (iat + validity.toSeconds());

    var ecdhPublicKey = new VauEccPublicKey((ECPublicKey) eccKyberKeyPair.eccKeyPair().getPublic());
    var kyberPublicKeyBytes =
        KyberKeys.extractCompactKyberPublicKey(eccKyberKeyPair.kyberKeyPair());
    return new VauPublicKeys(iat, exp, comment, ecdhPublicKey, kyberPublicKeyBytes);
  }

  public PublicKey kyberPublicKey() {
    return KyberKeys.decodeKyberPublicKey(kyberPublicKeyBytes);
  }

  @Override
  public String toString() {
    return "VauPublicKeys[iat=%d, exp=%d, comment='%s', ecdhPublicKey=%s, kyberPublicKeyBytes=%s]"
        .formatted(iat, exp, comment, ecdhPublicKey, Arrays.toString(kyberPublicKeyBytes));
  }
}
