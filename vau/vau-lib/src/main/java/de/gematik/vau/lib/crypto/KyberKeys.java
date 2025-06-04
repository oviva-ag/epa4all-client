package de.gematik.vau.lib.crypto;

import de.gematik.vau.lib.exceptions.VauKeyConversionException;
import de.gematik.vau.lib.exceptions.VauKyberCryptoException;
import de.gematik.vau.lib.util.ArrayUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.util.encoders.Hex;

public class KyberKeys {

  private KyberKeys() {}

  @SuppressWarnings("java:S2386")
  public static final byte[] KYBER_PUBLIC_KEY_ENCODING_HEADER =
      Hex.decode("308204B2300B0609608648016503040402038204A100");

  @SuppressWarnings("java:S2386")
  public static final byte[] KYBER_PRIVATE_KEY_ENCODING_HEADER =
      Hex.decode("30820978020100300B06096086480165030404020482096404820960");

  public static byte[] extractCompactKyberPublicKey(KeyPair kyberKeyPair) {
    try {
      final byte[] verbosePublicKey = kyberKeyPair.getPublic().getEncoded();
      final ASN1InputStream asn1InputStream =
          new ASN1InputStream(new ByteArrayInputStream(verbosePublicKey));
      return ((DERBitString) ((DLSequence) asn1InputStream.readObject()).getObjectAt(1)).getBytes();
    } catch (IOException e) {
      throw new VauKeyConversionException("Error during key extraction for Kyber-key", e);
    }
  }

  public static PublicKey decodeKyberPublicKey(byte[] keyBytes) {
    try {
      var x509EncodedKeySpec =
          new X509EncodedKeySpec(
              ArrayUtils.unionByteArrays(KYBER_PUBLIC_KEY_ENCODING_HEADER, keyBytes));
      var keyFactory = KeyFactory.getInstance("KYBER", "BCPQC");
      return keyFactory.generatePublic(x509EncodedKeySpec);
    } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new VauKyberCryptoException("Kyber Public Key Bytes are not well formed.", e);
    }
  }
}
