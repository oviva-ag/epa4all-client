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

@SuppressWarnings("java:S6218")
public record VauMessage2(
    @JsonProperty("MessageType") String messageType,
    @JsonProperty("ECDH_ct") VauEccPublicKey ecdhCt,
    @JsonProperty("Kyber768_ct") byte[] kyberCt,
    @JsonProperty("AEAD_ct") byte[] aeadCt) {

  public static VauMessage2 create(
      VauEccPublicKey ecdhCt, byte[] kyberCt, byte[] aeadCiphertextMessage2) {
    return new VauMessage2("M2", ecdhCt, kyberCt, aeadCiphertextMessage2);
  }
}
