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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.gematik.vau.lib.crypto.EllipticCurve;
import java.math.BigInteger;
import java.util.HexFormat;
import org.bouncycastle.jce.interfaces.ECPublicKey;

@SuppressWarnings("java:S6218")
public record VauEccPublicKey(
    @JsonProperty("crv") String crv,
    @JsonDeserialize(using = ForceByteArrayDeserializer.class) @JsonProperty("x") byte[] x,
    @JsonDeserialize(using = ForceByteArrayDeserializer.class) @JsonProperty("y") byte[] y) {

  public VauEccPublicKey(ECPublicKey eccPublicKey) {
    this(
        "P-256",
        eccPublicKey.getQ().getXCoord().getEncoded(),
        eccPublicKey.getQ().getYCoord().getEncoded());
  }

  public ECPublicKey toEcPublicKey() {
    return EllipticCurve.getPublicKeyFromCoordinates(
        new BigInteger(1, x, 0, x.length), new BigInteger(1, y, 0, y.length));
  }

  @Override
  public String toString() {
    var hex = HexFormat.of();
    return "VauEccPublicKey[crv=%s, x=%s, y=%s]".formatted(crv, hex.formatHex(x), hex.formatHex(y));
  }
}
