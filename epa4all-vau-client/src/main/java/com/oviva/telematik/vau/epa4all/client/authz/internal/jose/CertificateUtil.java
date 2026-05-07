package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Optional;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class CertificateUtil {
  private CertificateUtil() {}

  public static X509Certificate parseDer(byte[] bytes) throws CertificateException {
    try (var certInputStream = new ByteArrayInputStream(bytes)) {
      // MUST be bouncycastle to deal with the brainpool certificates
      var certFactory = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
      var cert = certFactory.generateCertificate(certInputStream);
      if (cert instanceof X509Certificate x509Cert) {
        return x509Cert;
      }
      throw new CertificateEncodingException("not an X.509 certificate");
    } catch (IOException | NoSuchProviderException e) {
      throw new CertificateException("failed to parse certificate", e);
    }
  }

  public static Optional<ASN1ObjectIdentifier> getProfessionOid(
      X509Certificate trustedSigningCertificate) {

    ASN1Encodable asn1Admission = null;
    try {
      asn1Admission =
          new X509CertificateHolder(trustedSigningCertificate.getEncoded())
              .getExtensions()
              .getExtensionParsedValue(ISISMTTObjectIdentifiers.id_isismtt_at_admission);
    } catch (IOException | CertificateEncodingException e) {
      throw new IllegalArgumentException("bad certificate", e);
    }

    var admissionInstance = AdmissionSyntax.getInstance(asn1Admission);

    var contents = admissionInstance.getContentsOfAdmissions();
    if (contents.length != 1) {
      return Optional.empty();
    }

    var content = contents[0];
    var profInfos = content.getProfessionInfos();
    if (profInfos.length != 1) {
      return Optional.empty();
    }

    var profInfo = profInfos[0];

    var oids = profInfo.getProfessionOIDs();
    if (oids.length != 1) {
      return Optional.empty();
    }

    return Optional.ofNullable(oids[0]);
  }
}
