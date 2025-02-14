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
import org.bouncycastle.jce.interfaces.ECPublicKey;

@SuppressWarnings("java:S6218")
public record VauMessage1(
    @JsonProperty("ECDH_PK") VauEccPublicKey ecdhPublicKey,
    @JsonProperty("Kyber768_PK") byte[] kyberPublicKeyBytes,
    @JsonProperty("MessageType") String messageType) {

  public static VauMessage1 fromClientKey(EccKyberKeyPair clientKey) {
    var ecdhPublicKey = new VauEccPublicKey((ECPublicKey) clientKey.eccKeyPair().getPublic());
    var kyberPublicKeyBytes = KyberKeys.extractCompactKyberPublicKey(clientKey.kyberKeyPair());
    return new VauMessage1(ecdhPublicKey, kyberPublicKeyBytes, "M1");
  }

  public PublicKey kyberPublicKey() {
    return KyberKeys.decodeKyberPublicKey(kyberPublicKeyBytes);
  }
}
