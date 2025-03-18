package com.oviva.telematik.vau.httpclient.internal.cert;

import static org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers.id_isismtt_at_admission;
import static org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers.id_isismtt_at_certHash;

import com.oviva.telematik.vau.httpclient.VauClientException;
import de.gematik.vau.lib.exceptions.VauException;
import java.io.IOException;
import java.security.*;
import java.security.cert.*;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.isismtt.ocsp.CertHash;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrustStoreValidator implements TrustValidator {

  private static final Logger log = LoggerFactory.getLogger(TrustStoreValidator.class);

  private static final ASN1ObjectIdentifier OID_EPA_VAU =
      new ASN1ObjectIdentifier("1.2.276.0.76.4.209");

  // for test/mock purposed
  static Clock clock = Clock.systemUTC();

  // A_24624-01/1
  private static final Duration ocspResponseAge = Duration.ofHours(24);

  private final KeyStore trustStore;

  public TrustStoreValidator(KeyStore trustStore) {
    this.trustStore = trustStore;
  }

  @Override
  public void validate(
      X509Certificate vauInstanceCertificate,
      X509Certificate vauIssuerCertificate,
      List<X509Certificate> certificateChain,
      byte[] ocspResponseDer)
      throws CertificateValidationException {

    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/latest/#A_24624-01
    verifyEndUserCertificate(vauInstanceCertificate, vauIssuerCertificate, certificateChain);

    verifyOcspResponse(vauInstanceCertificate, ocspResponseDer);

    verifyRole(vauInstanceCertificate);
  }

  private void verifyEndUserCertificate(
      X509Certificate vauInstanceCertificate,
      X509Certificate vauIssuerCertificate,
      List<X509Certificate> certificateChain)
      throws CertificateValidationException {
    var instanceIntermediates =
        Stream.concat(Stream.of(vauIssuerCertificate), certificateChain.stream()).toList();
    verifyTrustChainAgainstRoot(vauInstanceCertificate, instanceIntermediates);
  }

  private void verifyRole(X509Certificate endUserCertificate)
      throws CertificateValidationException {
    try {
      var asn1Admission =
          new X509CertificateHolder(endUserCertificate.getEncoded())
              .getExtensions()
              .getExtensionParsedValue(id_isismtt_at_admission);

      var admissionInstance = AdmissionSyntax.getInstance(asn1Admission);

      var contents = admissionInstance.getContentsOfAdmissions();
      if (contents.length != 1) {
        throw new CertificateValidationException(
            "expected exactly one admission content, got %d".formatted(contents.length));
      }

      var content = contents[0];
      var profInfos = content.getProfessionInfos();
      if (profInfos.length != 1) {
        throw new CertificateValidationException(
            "expected exactly one profession info, got %d".formatted(profInfos.length));
      }

      var profInfo = profInfos[0];

      var oids = profInfo.getProfessionOIDs();
      if (oids.length != 1) {
        throw new CertificateValidationException(
            "expected exactly one profession oid, got %d".formatted(oids.length));
      }

      var oid = oids[0];

      if (!oid.equals(OID_EPA_VAU)) {
        throw new CertificateValidationException(
            "expected OID %s, got %s".formatted(OID_EPA_VAU, oid));
      }

    } catch (IOException | CertificateEncodingException e) {
      throw new CertificateValidationException("bad certificate", e);
    }
  }

  private void verifyTrustChainAgainstRoot(
      X509Certificate endUserCertificate, List<X509Certificate> certificateChain)
      throws CertificateValidationException {

    try {
      var intermediates = new CollectionCertStoreParameters(certificateChain);

      var target = new X509CertSelector();
      target.setCertificate(endUserCertificate);

      var params = new PKIXBuilderParameters(trustStore, target);
      params.addCertStore(CertStore.getInstance("Collection", intermediates));

      // we'll check the OCSP/CRL response separately
      params.setRevocationEnabled(false);

      var builder = CertPathBuilder.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME);

      var result = (PKIXCertPathBuilderResult) builder.build(params);
      log.atDebug().log(
          "certificate '{}' verified with trust anchor: '{}'",
          endUserCertificate.getSubjectX500Principal().getName(),
          result.getTrustAnchor().getTrustedCert().getSubjectX500Principal().getName());

    } catch (CertPathBuilderException
        | NoSuchAlgorithmException
        | InvalidAlgorithmParameterException e) {
      var name = endUserCertificate.getSubjectX500Principal().getName();
      throw new CertificateValidationException(
          "failed to validate VAU server certificate, bad certificate: " + name, e);
    } catch (NoSuchProviderException | KeyStoreException e) {
      throw new VauException("unexpected crypto exception", e);
    }
  }

  private void verifyOcspResponse(X509Certificate vauInstanceCertificate, byte[] ocspResponseDer)
      throws CertificateValidationException {

    if (ocspResponseDer == null || ocspResponseDer.length == 0) {
      throw new CertificateValidationException("empty OCSP response");
    }

    var ocspResp = toOcspResponse(ocspResponseDer);
    if (ocspResp.getStatus() != OCSPResp.SUCCESSFUL) {
      throw new CertificateValidationException(
          "invalid OCSP response status: %d".formatted(ocspResp.getStatus()), null);
    }

    var basicOcspResp = getBasicOcspResp(ocspResp);

    var responderX500Name = basicOcspResp.getResponderId().toASN1Primitive().getName();

    // is the OCSP response valid?
    var ocspResponderCertificate =
        findTrustedCertificate(responderX500Name)
            .orElseThrow(
                () ->
                    new VauClientException(
                        "no OCSP responder certificate found for %s".formatted(responderX500Name)));
    verifyOcspResponseSignature(ocspResponderCertificate, basicOcspResp);
    verifyOcspResponseAge(basicOcspResp);

    // has exactly one response?
    var responses = basicOcspResp.getResponses();
    if (responses.length != 1) {
      throw new CertificateValidationException(
          "expected 1 OCSP response, got %d".formatted(responses.length));
    }
    var response = responses[0];

    // status for our cert good?
    if (response.getCertStatus() != CertificateStatus.GOOD) {
      throw new CertificateValidationException(
          "invalid OCSP response certificate status: %s".formatted(response.getCertStatus()));
    }

    verifyCertHash(vauInstanceCertificate, response);
  }

  Optional<X509Certificate> findTrustedCertificate(X500Name subject) {
    try {
      var enumeration = trustStore.aliases();
      while (enumeration.hasMoreElements()) {
        var alias = enumeration.nextElement();
        var cert = (X509Certificate) trustStore.getCertificate(alias);

        // making sure we have the bouncycastle X500Name for both
        var certPrincipal =
            new org.bouncycastle.asn1.x500.X500Name(cert.getSubjectX500Principal().getName());
        if (certPrincipal.equals(subject)) {
          return Optional.of(cert);
        }
      }
      return Optional.empty();
    } catch (KeyStoreException e) {
      throw new VauClientException(
          "cannot find certificate for %s in truststore".formatted(subject.toString()), e);
    }
  }

  void verifyOcspResponseAge(BasicOCSPResp ocspResponse) throws CertificateValidationException {
    var now = clock.instant();

    var producedAt = ocspResponse.getProducedAt().toInstant();

    if (Duration.between(producedAt, now).compareTo(ocspResponseAge) > 0) {
      throw new CertificateValidationException("OCSP response too old");
    }
  }

  /** Verifies teh cert hash of the parameterized OCSP Response against the certificate. */
  private void verifyCertHash(X509Certificate endUserCertificate, SingleResp ocspResponseEntry)
      throws CertificateValidationException {
    try {
      var singleOcspRespAsn1 =
          ocspResponseEntry.getExtension(id_isismtt_at_certHash).getParsedValue();
      var ocspCertHashBytes = CertHash.getInstance(singleOcspRespAsn1).getCertificateHash();
      var eeCertHashBytes = sha256(endUserCertificate.getEncoded());

      if (!MessageDigest.isEqual(ocspCertHashBytes, eeCertHashBytes)) {
        throw new CertificateValidationException(
            "ocsp cert hash does not match end user cert hash");
      }
    } catch (CertificateEncodingException e) {
      throw new CertificateValidationException("invalid end user certificate", e);
    }
  }

  private static byte[] sha256(byte[] bytes) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      return md.digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new VauException("SHA-256 not supported", e);
    }
  }

  protected void verifyOcspResponseSignature(
      X509Certificate vauIssuerCertificate, BasicOCSPResp ocspResponse)
      throws CertificateValidationException {
    try {
      var cvp =
          new JcaContentVerifierProviderBuilder()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .build(vauIssuerCertificate.getPublicKey());
      if (!ocspResponse.isSignatureValid(cvp)) {
        throw new CertificateValidationException("invalid OCSP response signature");
      }
    } catch (final OCSPException | OperatorCreationException e) {
      throw new CertificateValidationException("error validating OCSP response", e);
    }
  }

  public static OCSPResp toOcspResponse(byte[] ocspResponseDer)
      throws CertificateValidationException {
    try {
      return new OCSPResp(ocspResponseDer);
    } catch (IOException e) {
      throw new CertificateValidationException("failed to decode OCSP response", e);
    }
  }

  public static BasicOCSPResp getBasicOcspResp(OCSPResp ocspResponse)
      throws CertificateValidationException {
    try {
      return (BasicOCSPResp) ocspResponse.getResponseObject();
    } catch (OCSPException e) {
      throw new CertificateValidationException("failed to decode OCSP response", e);
    }
  }
}
