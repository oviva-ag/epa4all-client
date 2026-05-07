package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.bouncycastle.asn1.isismtt.x509.Admissions;
import org.bouncycastle.asn1.isismtt.x509.ProfessionInfo;
import org.bouncycastle.asn1.x500.DirectoryString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CertificateUtilTest {

  // RISE IDP test certificate (TEST-ONLY, NOT-VALID)
  private static final String IDP_CERT_PEM =
      """
                  MIIC9zCCAp6gAwIBAgIDALXqMAoGCCqGSM49BAMCMIGEMQswCQYDVQQGEwJERTEf\
                  MB0GA1UECgwWZ2VtYXRpayBHbWJIIE5PVC1WQUxJRDEyMDAGA1UECwwpS29tcG9u\
                  ZW50ZW4tQ0EgZGVyIFRlbGVtYXRpa2luZnJhc3RydWt0dXIxIDAeBgNVBAMMF0dF\
                  TS5LT01QLUNBNTYgVEVTVC1PTkxZMB4XDTI2MDEyMTE1MzkzOFoXDTMxMDEyMDE1\
                  MzkzN1owfTELMAkGA1UEBhMCQVQxKDAmBgNVBAoMH1JJU0UgR21iSCBURVNULU9O\
                  TFkgLSBOT1QtVkFMSUQxKTAnBgNVBAUTIDM4Nzc4LVYwMUkwMDA0VDIwMjYwMTIx\
                  MTUyMzI4MjExMRkwFwYDVQQDDBBkaXNjLnJ1LmlkcC5yaXNlMFowFAYHKoZIzj0C\
                  AQYJKyQDAwIIAQEHA0IABGL+nmChjSvGhVBH/o14iuUsK9CSZBAyO+UCNs6D7nZa\
                  O5xaTLrVNCdA4Zb+HjjoCucQjahDYZfmvu3CzCf4RAajggECMIH/MB0GA1UdDgQW\
                  BBSOp8MJLLkrkstNfHHkAKwwUrJUHTAfBgNVHSMEGDAWgBTVuBx5iaOlrcWNtv5b\
                  /hA3A50DwzBNBggrBgEFBQcBAQRBMD8wPQYIKwYBBQUHMAGGMWh0dHA6Ly9kb3du\
                  bG9hZC10ZXN0cmVmLmNybC50aS1kaWVuc3RlLmRlL29jc3AvZWMwDgYDVR0PAQH/\
                  BAQDAgeAMCEGA1UdIAQaMBgwCgYIKoIUAEwEgSMwCgYIKoIUAEwEgUswDAYDVR0T\
                  AQH/BAIwADAtBgUrJAgDAwQkMCIwIDAeMBwwGjAMDApJRFAtRGllbnN0MAoGCCqC\
                  FABMBIIEMAoGCCqGSM49BAMCA0cAMEQCIEYDbjgvR6IbcNQxGv1FQKg0qCqHlfBl\
                  8kbrNXXOF3+aAiBYjajQzxmWpQAewatkepSE8HQtBLaRNAnWGvgmxLRWFQ==""";

  // oid_idpd_gematik: IDP-Dienst OID per gematik gemSpec_OID
  private static final ASN1ObjectIdentifier IDP_DIENST_OID =
      new ASN1ObjectIdentifier("1.2.276.0.76.4.260");

  @BeforeAll
  static void setUp() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void parseDer_validCertificate_returnsX509Certificate() throws CertificateException {
    var derBytes = Base64.getDecoder().decode(IDP_CERT_PEM);

    var cert = CertificateUtil.parseDer(derBytes);

    assertNotNull(cert);
    assertTrue(cert.getSubjectX500Principal().getName().contains("CN=disc.ru.idp.rise"));
  }

  @Test
  void parseDer_invalidBytes_throwsCertificateException() {
    var garbage = new byte[] {0x00, 0x01, 0x02, 0x03};

    assertThrows(CertificateException.class, () -> CertificateUtil.parseDer(garbage));
  }

  @Test
  void parseDer_emptyBytes_throwsCertificateException() {
    assertThrows(CertificateException.class, () -> CertificateUtil.parseDer(new byte[0]));
  }

  @Test
  void getProfessionOid_idpCertificate_returnsIdpDienstOid() throws CertificateException {
    var derBytes = Base64.getDecoder().decode(IDP_CERT_PEM);
    var cert = CertificateUtil.parseDer(derBytes);

    var oid = CertificateUtil.getProfessionOid(cert);

    assertTrue(oid.isPresent());
    assertEquals(IDP_DIENST_OID, oid.get());
  }

  @Test
  void getProfessionOid_multipleAdmissions_returnsEmpty() throws Exception {
    var profInfo = buildProfInfo(IDP_DIENST_OID);
    var admissions1 = new Admissions(null, null, new ProfessionInfo[] {profInfo});
    var admissions2 = new Admissions(null, null, new ProfessionInfo[] {profInfo});
    var admissionSyntax =
        new AdmissionSyntax(null, new DERSequence(new ASN1Encodable[] {admissions1, admissions2}));
    var cert = buildCertWithAdmission(admissionSyntax);

    var result = CertificateUtil.getProfessionOid(cert);

    assertTrue(result.isEmpty());
  }

  @Test
  void getProfessionOid_multipleProfessionInfos_returnsEmpty() throws Exception {
    var profInfo1 = buildProfInfo(IDP_DIENST_OID);
    var profInfo2 = buildProfInfo(IDP_DIENST_OID);
    var admissions = new Admissions(null, null, new ProfessionInfo[] {profInfo1, profInfo2});
    var admissionSyntax = new AdmissionSyntax(null, new DERSequence(admissions));
    var cert = buildCertWithAdmission(admissionSyntax);

    var result = CertificateUtil.getProfessionOid(cert);

    assertTrue(result.isEmpty());
  }

  @Test
  void getProfessionOid_multipleOids_returnsEmpty() throws Exception {
    var unrelatedOid = new ASN1ObjectIdentifier("1.2.276.0.76.4.999");
    var profInfo = buildProfInfo(IDP_DIENST_OID, unrelatedOid);
    var admissions = new Admissions(null, null, new ProfessionInfo[] {profInfo});
    var admissionSyntax = new AdmissionSyntax(null, new DERSequence(admissions));
    var cert = buildCertWithAdmission(admissionSyntax);

    var result = CertificateUtil.getProfessionOid(cert);

    assertTrue(result.isEmpty());
  }

  @Test
  void getProfessionOid_noOids_returnsEmpty() throws Exception {
    var profInfo = buildProfInfo();
    var admissions = new Admissions(null, null, new ProfessionInfo[] {profInfo});
    var admissionSyntax = new AdmissionSyntax(null, new DERSequence(admissions));
    var cert = buildCertWithAdmission(admissionSyntax);

    var result = CertificateUtil.getProfessionOid(cert);

    assertTrue(result.isEmpty());
  }

  // -- helpers --

  private static ProfessionInfo buildProfInfo(ASN1ObjectIdentifier... oids) {
    return new ProfessionInfo(null, new DirectoryString[0], oids, null, null);
  }

  private static X509Certificate buildCertWithAdmission(AdmissionSyntax admissionSyntax)
      throws Exception {
    var keyPair = generateKeyPair();
    var now = Instant.now();
    var name = new X500Name("CN=Test");
    var certBuilder =
        new JcaX509v3CertificateBuilder(
            name,
            BigInteger.ONE,
            Date.from(now.minusSeconds(60)),
            Date.from(now.plusSeconds(3600)),
            name,
            keyPair.getPublic());
    certBuilder.addExtension(
        ISISMTTObjectIdentifiers.id_isismtt_at_admission, false, admissionSyntax);
    var signer =
        new JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.getPrivate());
    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(certBuilder.build(signer));
  }

  private static KeyPair generateKeyPair() throws Exception {
    var kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
    kpg.initialize(new ECGenParameterSpec("brainpoolP256r1"));
    return kpg.generateKeyPair();
  }
}
