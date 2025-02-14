package com.oviva.telematik.vau.httpclient.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.internal.cert.CertificateValidationException;
import com.oviva.telematik.vau.httpclient.internal.cert.TrustValidator;
import com.oviva.telematik.vau.httpclient.internal.cert.VauCertificateClient;
import de.gematik.vau.lib.data.SignedPublicKeysTrustValidator;
import java.net.URI;
import java.security.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignedPublicKeysTrustValidatorImpl implements SignedPublicKeysTrustValidator {

  private static final Logger log =
      LoggerFactory.getLogger(SignedPublicKeysTrustValidatorImpl.class);
  private final VauCertificateClient certDataClient;
  private final URI vauUri;

  public SignedPublicKeysTrustValidatorImpl(
      HttpClient outerClient, TrustValidator trustValidator, URI vauUri) {
    this.vauUri = vauUri;
    certDataClient = new VauCertificateClient(outerClient, trustValidator);
  }

  @Override
  public boolean isTrusted(SignedPublicKeys signedPublicKeys) {
    try {
      var certs =
          certDataClient.fetchAndValidate(
              vauUri,
              signedPublicKeys.certHash(),
              signedPublicKeys.cdv(),
              signedPublicKeys.ocspResponse());

      var ecdsa = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
      ecdsa.initVerify(certs.cert());
      ecdsa.update(signedPublicKeys.signedPubKeys());

      // https://www.rfc-editor.org/rfc/rfc7515.html#appendix-A.3.1
      var signatureDer = ECDSA.transcodeSignatureToDER(signedPublicKeys.signatureEs256());
      ecdsa.verify(signatureDer);

    } catch (CertificateValidationException
        | InvalidKeyException
        | SignatureException
        | NoSuchAlgorithmException
        | JOSEException
        | NoSuchProviderException e) {
      log.atDebug().setCause(e).log("failed to validate certificate: {}", e.getMessage());
      return false;
    }
    return true;
  }
}
