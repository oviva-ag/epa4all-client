package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import static org.junit.jupiter.api.Assertions.*;

import java.security.Security;
import java.security.cert.CertificateException;
import java.util.Base64;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
  void getProfessionOid_idpCertificate_returnsIdpDienstOid() throws CertificateException {
    var derBytes = Base64.getDecoder().decode(IDP_CERT_PEM);
    var cert = CertificateUtil.parseDer(derBytes);

    var oid = CertificateUtil.getProfessionOid(cert);

    assertTrue(oid.isPresent());
    assertEquals(IDP_DIENST_OID, oid.get());
  }
}
