package de.gematik.vau.lib.data;

import java.util.HexFormat;

public interface SignedPublicKeysTrustValidator {

  boolean isTrusted(SignedPublicKeys signedPublicKeys);

  /**
   * keys according to <a
   * href="https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/gemSpec_Krypt_V2.37.0/#A_24425-01">A_24425-01</a>
   * *
   */
  @SuppressWarnings("java:S6218")
  record SignedPublicKeys(
      byte[] signedPubKeys, byte[] signatureEs256, byte[] certHash, int cdv, byte[] ocspResponse) {
    @Override
    public String toString() {
      var hex = HexFormat.of();
      return "SignedPublicKeys[signedPubKeys='%s', signatureEs256='%s', certHash='%s', cdv=%s, ocspResponse='%s']"
          .formatted(
              hex.formatHex(signedPubKeys),
              hex.formatHex(signatureEs256),
              hex.formatHex(certHash),
              cdv,
              hex.formatHex(ocspResponse));
    }
  }
}
