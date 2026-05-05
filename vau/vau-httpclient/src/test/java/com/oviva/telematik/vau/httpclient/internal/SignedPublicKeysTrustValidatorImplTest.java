package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oviva.telematik.vau.httpclient.internal.cert.CertData;
import com.oviva.telematik.vau.httpclient.internal.cert.CertificateValidationException;
import com.oviva.telematik.vau.httpclient.internal.cert.VauCertificateClient;
import de.gematik.vau.lib.data.SignedPublicKeysTrustValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Test;

class SignedPublicKeysTrustValidatorImplTest {

  static {
    Security.addProvider(new BouncyCastlePQCProvider());
    Security.addProvider(new BouncyCastleProvider());
  }

  private static final URI VAU_URI = URI.create("https://vau.example.com/epa");

  @Test
  void test_signatureVerify() throws JsonProcessingException, CertificateValidationException {

    var keys = mockKeys();

    var certClient = mock(VauCertificateClient.class);
    when(certClient.fetchAndValidate(VAU_URI, keys.certHash(), keys.cdv(), keys.ocspResponse()))
        .thenReturn(mockCerts());

    var sut = new SignedPublicKeysTrustValidatorImpl(certClient, VAU_URI);

    // when & then
    assertTrue(sut.isTrusted(keys));
  }

  @Test
  void test_signatureVerify_invalidCertificate()
      throws JsonProcessingException, CertificateValidationException {

    var keys = mockKeys();

    var certClient = mock(VauCertificateClient.class);
    doThrow(CertificateValidationException.class)
        .when(certClient)
        .fetchAndValidate(VAU_URI, keys.certHash(), keys.cdv(), keys.ocspResponse());

    var sut = new SignedPublicKeysTrustValidatorImpl(certClient, VAU_URI);

    // when & then
    assertFalse(sut.isTrusted(keys));
  }

  @Test
  void test_signatureVerify_bogusSignature()
      throws JsonProcessingException, CertificateValidationException {

    var keys = mockKeys();

    var certClient = mock(VauCertificateClient.class);
    when(certClient.fetchAndValidate(VAU_URI, keys.certHash(), keys.cdv(), keys.ocspResponse()))
        .thenReturn(mockCerts());

    var sut = new SignedPublicKeysTrustValidatorImpl(certClient, VAU_URI);

    var bogusSignature = bogusSignature();
    var bogusKeys =
        new SignedPublicKeysTrustValidator.SignedPublicKeys(
            keys.signedPubKeys(), bogusSignature, keys.certHash(), keys.cdv(), keys.ocspResponse());

    // when & then
    assertFalse(sut.isTrusted(bogusKeys));
  }

  private byte[] bogusSignature() {
    var bogusSignature = new byte[64];
    new Random(42).nextBytes(bogusSignature);
    return bogusSignature;
  }

  private SignedPublicKeysTrustValidator.SignedPublicKeys mockKeys()
      throws JsonProcessingException {

    var rawKeys =
        """
            {"signedPubKeys":"v2dFQ0RIX1BLv2NjcnZlUC0yNTZheFggmBLiupDaEHmFsHMIjeVCv1NWYaPZB/iRhnymsRlxm2FheVggCltXimLzYh8c8IrIu4yvRsHEGx/UJCxcQ51bv/5xo1L/a0t5YmVyNzY4X1BLWQSgPIcF1alKbqYrJzcxBYgjipRDzftYk/qpgGFBCzZ5ILBzW6J+Q6Sg37tYTcIzh7sn/ADBtVcTXZsXOHG7e4TFcAa9Y4uGPLB8ESu+zhDE6MeLQJjPQghopLTF5cVjqqq2VSAgvVzJVYZoSpN0LKtG8NhYBSOUOda13SljExNHcowqQrjHQeOhFoinubcAxAKpl0cwPJCtQ5YBdcSvkooWtuUBfxQkQCcCVNBYHYde1KFOj4TA9fDA9KAaaiRSueXGivk5GaSZ9ic3EjKgi8UinZzNjNGjDCcXYhHBbCTFfWxtjlQXuCqZ8CQBOWQ7cvMpNytH1udcPvm304U156CwPApa7umrWplxqGJ+UXU1q+M/oOZKARS/3bEqxIrFrqSKYOR7y5BI7Bxg1hpjMVQSibavuaEKaaYn/CxpzaHMfGlgWnGZIyMto3pHVKQE7byH1my7roZNoputHNga9WrEzgZq/XgaW2hX3uLM54cOZPyNFjzPsjGiLqwSMKa8ImqcxXPPLHlcOzWdyCoI1CLOJddhxHSXozy8cDWeMFjDN+K9pyqY1UcCl8gfTtIo0GaDoStaTNGoTYS+RsdoX4cxWrlGp3GaraOpQHduXaNFJly10ORVKkFqOam8USKBUtkpToo1yRB5pXy9kkhfqbKVO8Oha3vOSfCcTTdAIrhibeXBAlE/HSRoCDBcFZe89XtbtbQoC6qm3ryF6jghKUkpDBC/Ieq92sQtxTWhMxxAkwmH7wakc6EFfiCtlbHHZ2dU5aOosJsUPiQfr0NImcey+8tlI/EkZ7yG0DnKUCU/umOtYlJfvRFJ/KoGGhBKVpaSDbyUtmg+AMoKW4oFNQq/PtxGhsCK6Bh33EJjJ3RQVvqyL3ZoQeB2RfomY4RPqvkWhmGH2yYVcmygErUJLoq9yPIX57CsOxPJu7QYLbhbI+kimVqd/lGxH/yICKfHstJyIWyyPmGHmaO42iEfC/dib1x4zJyqMZc5uDqoLKhMDDOCH3ZoLrkm+IIVq+aVK5wkiynD69s45RaPHJYuYPIotmCjkUM9zbnBKEsdDLWMq7aGrtFqjfGvvJu/+LOXF5JotYQd6oVCKRm4DoeM3UM5hTMd45lXHLwn6Dq3EeCeBWaQwyk/b8toSRYweJe67lsyYkUOfQSQkRQWWMxuisUVEZxXcRQJjCvCmPW+7dBc0kgb5esiZqAzZfJttVS3sWYGOmgdlmuDB6Gly/mZ+pMxu6RVGyF9dVd6KjG5e1MK7uDFNSMhqAQ3VeKYKiY19eyFYFxsR+mB54cW1eJ+63jJsSFe/DGeckOv/kxAbdgdFyFRKWWzHVaIjfKqcGXEYpfAUmdGH+oQvgu1kdNQSLMrJOJb6cgflPmpvQl6ODjMdOkHP2WSm1NH5BfCUqMSUda+A8uEMmubCfVcVCSEyCI9CpKC0BaVq3jP4CUxGjfG3OkoQLCntspGoqFn11YONRlUdYK3pYiUk6sED0O6G0lYWYBGgbxzSSu/ZWd8nOW/dBO2MRnPvSxhhVqEd4W+60gNmXx1j0dzfKl1mQPRY532Qe+XJ1UCWfooqmOqYTNZaLJjaWF0Gmn5x8hjZXhwGmoEU8hnY29tbWVudHhAY3JlYXRlZCBieSBJQk0gVEVFLCBidWlsZCB2ZXJzaW9uIHYtNC4zMS41NzMsIGJ1aWxkIHR5cGUgUmVsZWFzZf8=","signatureEs256":"KKG6TPMOT3dOVSVpomK+ud57tQZYMnCYm5HnHTIPhrXPBC0gl2nImX+laxcsY4fgmKCvWX1zlfV1BV4IazmJ0w==","certHash":"yHBDdascJzWERBnmALRL8+lqWQQbP5CXMeUyFggfnU8=","cdv":1,"ocspResponse":"MIIEVQoBAKCCBE4wggRKBgkrBgEFBQcwAQEEggQ7MIIENzCCAQ2hYjBgMQswCQYDVQQGEwJERTEmMCQGA1UECgwdYXJ2YXRvIFN5c3RlbXMgR21iSCBOT1QtVkFMSUQxKTAnBgNVBAMMIEtvbXAtQ0E1NSBPQ1NQLVNpZ25lcjIgVEVTVC1PTkxZGA8yMDI2MDUwNTEyMzczM1owgZUwgZIwOzAJBgUrDgMCGgUABBTezI4dOhPLoPJDPISHi+EVtpSiFwQUHmWV/ABjHrP0TVqJEvoc0L8+fjACAhrFgAAYDzIwMjYwNTA1MTIzNzMzWqFAMD4wPAYFKyQIAw0EMzAxMA0GCWCGSAFlAwQCAQUABCDIcEN1qxwnNYREGeYAtEvz6WpZBBs/kJcx5TIWCB+dTzAKBggqhkjOPQQDAgNJADBGAiEAi64o00Sf3OIXiGwTQEldIizazN1ljoll3foXWBe/ngICIQCETxIxe4AUvAHOegQOYtwaH9bb12FiyX34i2jZqTa5x6CCAsswggLHMIICwzCCAmmgAwIBAgICAKswCgYIKoZIzj0EAwIwgYQxCzAJBgNVBAYTAkRFMR8wHQYDVQQKDBZnZW1hdGlrIEdtYkggTk9ULVZBTElEMTIwMAYDVQQLDClLb21wb25lbnRlbi1DQSBkZXIgVGVsZW1hdGlraW5mcmFzdHJ1a3R1cjEgMB4GA1UEAwwXR0VNLktPTVAtQ0E1NSBURVNULU9OTFkwHhcNMjQwMTA5MTAzNDAwWhcNMjkwMTA3MTAzMzU5WjBgMQswCQYDVQQGEwJERTEmMCQGA1UECgwdYXJ2YXRvIFN5c3RlbXMgR21iSCBOT1QtVkFMSUQxKTAnBgNVBAMMIEtvbXAtQ0E1NSBPQ1NQLVNpZ25lcjIgVEVTVC1PTkxZMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXnMQ6WIGwYvhhcdWhYyjkHm4RtL9ITAZOhZ0wpvGb0vqSxV8LWzcvuIEAyr+ucG/0XglafZElyaF82SPGRJn+qOB7TCB6jAdBgNVHQ4EFgQUEC0XOMYxHUbZsg2ZKAfYxD9I9KMwHwYDVR0jBBgwFoAUHmWV/ABjHrP0TVqJEvoc0L8+fjAwTQYIKwYBBQUHAQEEQTA/MD0GCCsGAQUFBzABhjFodHRwOi8vZG93bmxvYWQtdGVzdHJlZi5jcmwudGktZGllbnN0ZS5kZS9vY3NwL2VjMA4GA1UdDwEB/wQEAwIGQDAVBgNVHSAEDjAMMAoGCCqCFABMBIEjMAwGA1UdEwEB/wQCMAAwEwYDVR0lBAwwCgYIKwYBBQUHAwkwDwYJKwYBBQUHMAEFBAIFADAKBggqhkjOPQQDAgNIADBFAiEAvCbUgcI1u0b1FYw1pQgVLzAKLDhDZH4FV2XB3NoK/dcCIDkkACnT50kLaDN9SEx2spxBMYSI5oA5q5fRQPhcZaKG"}
            """;

    return new ObjectMapper()
        .readValue(rawKeys, SignedPublicKeysTrustValidator.SignedPublicKeys.class);
  }

  private CertData mockCerts() {

    var cert =
        mustParseDer(
"""
MIIC5TCCAoygAwIBAgICGsUwCgYIKoZIzj0EAwIwgYQxCzAJBgNVBAYTAkRFMR8wHQYDVQQKDBZn
ZW1hdGlrIEdtYkggTk9ULVZBTElEMTIwMAYDVQQLDClLb21wb25lbnRlbi1DQSBkZXIgVGVsZW1h
dGlraW5mcmFzdHJ1a3R1cjEgMB4GA1UEAwwXR0VNLktPTVAtQ0E1NSBURVNULU9OTFkwHhcNMjQx
MTIwMDk1MzI1WhcNMjkxMTE5MDk1MzI0WjBLMQswCQYDVQQGEwJERTEiMCAGA1UECgwZSUJNIFRF
U1QtT05MWSAtIE5PVC1WQUxJRDEYMBYGA1UEAwwPVkFVLUFVVC1FUEEtSUJNMFkwEwYHKoZIzj0C
AQYIKoZIzj0DAQcDQgAEUoy2zS1DSzM/zKosesaIa3Vmb+Du2nilbBVkuc6ZYAiNZ+F/H9ehtJEL
K4idqkl1iCnPmEW5By+caWKFFFGYjaOCASQwggEgMB0GA1UdDgQWBBTy1pqIQCDs1+koR2bqVlR8
0sRfETAfBgNVHSMEGDAWgBQeZZX8AGMes/RNWokS+hzQvz5+MDBNBggrBgEFBQcBAQRBMD8wPQYI
KwYBBQUHMAGGMWh0dHA6Ly9kb3dubG9hZC10ZXN0cmVmLmNybC50aS1kaWVuc3RlLmRlL29jc3Av
ZWMwDgYDVR0PAQH/BAQDAgeAMCEGA1UdIAQaMBgwCgYIKoIUAEwEgSMwCgYIKoIUAEwEgRswDAYD
VR0TAQH/BAIwADBOBgUrJAgDAwRFMEMwQTA/MD0wOzAtDCtlUEEgdmVydHJhdWVuc3fDvHJkaWdl
IEF1c2bDvGhydW5nc3VtZ2VidW5nMAoGCCqCFABMBIFRMAoGCCqGSM49BAMCA0cAMEQCIGKDlh+Y
1isYJ20DZ03XH953iaoTH8HPVrzOukeGHV4YAiBtzOCYw+KsRuNOqLslKu75Wrf3XY9qRng3ri8h
0Rn7dg
""");

    var ca =
        mustParseDer(
"""
MIIC8TCCApigAwIBAgIBCjAKBggqhkjOPQQDAjCBgTELMAkGA1UEBhMCREUxHzAdBgNVBAoMFmdl
bWF0aWsgR21iSCBOT1QtVkFMSUQxNDAyBgNVBAsMK1plbnRyYWxlIFJvb3QtQ0EgZGVyIFRlbGVt
YXRpa2luZnJhc3RydWt0dXIxGzAZBgNVBAMMEkdFTS5SQ0E3IFRFU1QtT05MWTAeFw0yMzA3MTEx
MTI0MTJaFw0zMTA3MDkxMTI0MTFaMIGEMQswCQYDVQQGEwJERTEfMB0GA1UECgwWZ2VtYXRpayBH
bWJIIE5PVC1WQUxJRDEyMDAGA1UECwwpS29tcG9uZW50ZW4tQ0EgZGVyIFRlbGVtYXRpa2luZnJh
c3RydWt0dXIxIDAeBgNVBAMMF0dFTS5LT01QLUNBNTUgVEVTVC1PTkxZMFkwEwYHKoZIzj0CAQYI
KoZIzj0DAQcDQgAEsNwTdQTsntXYLp4Xy9TFDRNkVeNv2kA80uMpUMlAb9XSZCh2Gyz64UboKm7m
4VagLyPoGuWzAFQ6qEfkT0E+CqOB+zCB+DAdBgNVHQ4EFgQUHmWV/ABjHrP0TVqJEvoc0L8+fjAw
HwYDVR0jBBgwFoAUsvAJPk0L4wgkgJY1bjo2MyvySxowSgYIKwYBBQUHAQEEPjA8MDoGCCsGAQUF
BzABhi5odHRwOi8vb2NzcC10ZXN0cmVmLnJvb3QtY2EudGktZGllbnN0ZS5kZS9vY3NwMA4GA1Ud
DwEB/wQEAwIBBjBGBgNVHSAEPzA9MDsGCCqCFABMBIEjMC8wLQYIKwYBBQUHAgEWIWh0dHA6Ly93
d3cuZ2VtYXRpay5kZS9nby9wb2xpY2llczASBgNVHRMBAf8ECDAGAQH/AgEAMAoGCCqGSM49BAMC
A0cAMEQCIAcCpEG/g4t2tKWtAXfU3fIlJ49LLbPh37RLq2Xf8/IkAiAXrmaRDv5GPGH8QPdqP7D1
+NXWu1yMJu956LoSkQDoZA
""");

    var chain =
        Arrays.stream(
                """
        MIICyzCCAnKgAwIBAgIBATAKBggqhkjOPQQDAjCBgTELMAkGA1UEBhMCREUxHzAdBgNVBAoMFmdl
        bWF0aWsgR21iSCBOT1QtVkFMSUQxNDAyBgNVBAsMK1plbnRyYWxlIFJvb3QtQ0EgZGVyIFRlbGVt
        YXRpa2luZnJhc3RydWt0dXIxGzAZBgNVBAMMEkdFTS5SQ0E1IFRFU1QtT05MWTAeFw0yMTA3MjIx
        MjU0MTFaFw0zMTA3MjAxMjU0MTFaMIGBMQswCQYDVQQGEwJERTEfMB0GA1UECgwWZ2VtYXRpayBH
        bWJIIE5PVC1WQUxJRDE0MDIGA1UECwwrWmVudHJhbGUgUm9vdC1DQSBkZXIgVGVsZW1hdGlraW5m
        cmFzdHJ1a3R1cjEbMBkGA1UEAwwSR0VNLlJDQTUgVEVTVC1PTkxZMFowFAYHKoZIzj0CAQYJKyQD
        AwIIAQEHA0IABJukjjeYlo6B3WTeNVof861qQRIa3ZcAkUyj1zMER6I+aley7K/U1XCFQ72ADk9q
        oRAYNspYA1dVQiFsXML32PWjgdcwgdQwHQYDVR0OBBYEFOGt4Af80iB5JPTcl70yZM1rFIUJMEoG
        CCsGAQUFBwEBBD4wPDA6BggrBgEFBQcwAYYuaHR0cDovL29jc3AtdGVzdHJlZi5yb290LWNhLnRp
        LWRpZW5zdGUuZGUvb2NzcDAOBgNVHQ8BAf8EBAMCAQYwRgYDVR0gBD8wPTA7BggqghQATASBIzAv
        MC0GCCsGAQUFBwIBFiFodHRwOi8vd3d3LmdlbWF0aWsuZGUvZ28vcG9saWNpZXMwDwYDVR0TAQH/
        BAUwAwEB/zAKBggqhkjOPQQDAgNHADBEAiAGnycg02dlaa1JGjN2g2NGc28jj4yuHQZrOb0yDWrg
        VQIgBRqGkgNF8R2HTjHZpW/ImKbvHoO6iV1AwzfFl1uzdG0,
        MIIDtjCCA12gAwIBAgIBDDAKBggqhkjOPQQDAjCBgTELMAkGA1UEBhMCREUxHzAdBgNVBAoMFmdl
        bWF0aWsgR21iSCBOT1QtVkFMSUQxNDAyBgNVBAsMK1plbnRyYWxlIFJvb3QtQ0EgZGVyIFRlbGVt
        YXRpa2luZnJhc3RydWt0dXIxGzAZBgNVBAMMEkdFTS5SQ0E1IFRFU1QtT05MWTAeFw0yMTEwMjgw
        NzM0MjZaFw0zMTA3MjAwNzM0MjVaMIGBMQswCQYDVQQGEwJERTEfMB0GA1UECgwWZ2VtYXRpayBH
        bWJIIE5PVC1WQUxJRDE0MDIGA1UECwwrWmVudHJhbGUgUm9vdC1DQSBkZXIgVGVsZW1hdGlraW5m
        cmFzdHJ1a3R1cjEbMBkGA1UEAwwSR0VNLlJDQTYgVEVTVC1PTkxZMIIBIjANBgkqhkiG9w0BAQEF
        AAOCAQ8AMIIBCgKCAQEAvnQeiBEfnRD7wzhhF7Ah0LnVKdm7XkhQfrVbfIcJSmFyIWXYJhrui3oY
        ErcVBDhcEiHqB8EptvyiPW4TH76LTq1ea6ulvr/OzdwnMc8N9RiYjiPr4rLo/8SBPo0crxfAUkLV
        mnokipGkv+AESuCfzFmNnd1D1pd/NI3dF1++QWZ1CT4VlYEL73YQko4DRlyIVJl/LPNZXwCmImlW
        CkNABVINRXyKhG2AAmOYKrJQ0DhC17HadToLwd1jKtfYqHjC28kdPeVA30hQY4C+Wb6XeAAFAnru
        Y6lBkeav6i2Do64Plac+8nzYhhHwU4dHinYcpz/FN3nhzu87eX5qyVY1XwIDAQABo4H4MIH1MB0G
        A1UdDgQWBBRM9+BlWFWY5jmLyAd1PUymcCzPKTAfBgNVHSMEGDAWgBThreAH/NIgeST03Je9MmTN
        axSFCTBKBggrBgEFBQcBAQQ+MDwwOgYIKwYBBQUHMAGGLmh0dHA6Ly9vY3NwLXRlc3RyZWYucm9v
        dC1jYS50aS1kaWVuc3RlLmRlL29jc3AwDgYDVR0PAQH/BAQDAgEGMEYGA1UdIAQ/MD0wOwYIKoIU
        AEwEgSMwLzAtBggrBgEFBQcCARYhaHR0cDovL3d3dy5nZW1hdGlrLmRlL2dvL3BvbGljaWVzMA8G
        A1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDRwAwRAIgFL1kx8WwpvE6Z1Qgxp3hVuuFmtJboMFp
        hPnqrSnI0bECICDH1I7wiv/0M9F+OtOryHifOrGUXc13uj0vjULnPMMo,
        MIIDrTCCApWgAwIBAgIBKzANBgkqhkiG9w0BAQsFADCBgTELMAkGA1UEBhMCREUxHzAdBgNVBAoM
        FmdlbWF0aWsgR21iSCBOT1QtVkFMSUQxNDAyBgNVBAsMK1plbnRyYWxlIFJvb3QtQ0EgZGVyIFRl
        bGVtYXRpa2luZnJhc3RydWt0dXIxGzAZBgNVBAMMEkdFTS5SQ0E2IFRFU1QtT05MWTAeFw0yMzA1
        MjUxMzAyMjJaFw0zMTEwMjYwNzI0MTRaMIGBMQswCQYDVQQGEwJERTEfMB0GA1UECgwWZ2VtYXRp
        ayBHbWJIIE5PVC1WQUxJRDE0MDIGA1UECwwrWmVudHJhbGUgUm9vdC1DQSBkZXIgVGVsZW1hdGlr
        aW5mcmFzdHJ1a3R1cjEbMBkGA1UEAwwSR0VNLlJDQTcgVEVTVC1PTkxZMFkwEwYHKoZIzj0CAQYI
        KoZIzj0DAQcDQgAEGv3lzIASzKQHW0YbxoaSIFUlGcgH8c/JEWOifqVVKkJUS81zG1ogcL6skAhG
        CtkksfdSJKiZnmnKeQ/yAgGZUaOB+DCB9TAdBgNVHQ4EFgQUsvAJPk0L4wgkgJY1bjo2MyvySxow
        HwYDVR0jBBgwFoAUTPfgZVhVmOY5i8gHdT1MpnAszykwSgYIKwYBBQUHAQEEPjA8MDoGCCsGAQUF
        BzABhi5odHRwOi8vb2NzcC10ZXN0cmVmLnJvb3QtY2EudGktZGllbnN0ZS5kZS9vY3NwMA4GA1Ud
        DwEB/wQEAwIBBjBGBgNVHSAEPzA9MDsGCCqCFABMBIEjMC8wLQYIKwYBBQUHAgEWIWh0dHA6Ly93
        d3cuZ2VtYXRpay5kZS9nby9wb2xpY2llczAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUA
        A4IBAQCK4eAldg1bck+LdGU+ebCYtW/ceqd9fSmmiOTZw1cldBsK+/ouQh7d9KcaLmpdr1y3JFnA
        zzwZtaEbD0B41grVmKYTXVMYur+mt5ypX8fMg1FK95CRJa7gfULNPrInP5qpVJnTK1pJuh0Lf3zR
        JLCOpTNoXJoHjOZaa4xfS300chKkBQUtAqjOcxSex4JBUAoX1GVKDNNwFoR5/gBQ0bNuRYMNzvhC
        5ZIV+AL0IKdyWIMmgs1cZgRvASuLcvNTcYhna4qzXgwfWRKzpgdW4aTUh6/GuatTLWSqvHl55Uy5
        gVqPL6P9p7hks0O1Zx4cNgDOO2Wu2gOh0ENC+r1pPf6K
        """
                    .split(","))
            .map(String::strip)
            .map(SignedPublicKeysTrustValidatorImplTest::mustParseDer)
            .toList();
    return new CertData(cert, ca, chain);
  }

  private static X509Certificate mustParseDer(String certBase64) {
    try (var certInputStream =
        new ByteArrayInputStream(Base64.getMimeDecoder().decode(certBase64))) {
      // MUST be bouncycastle to deal with the brainpool certificates
      var certFactory = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
      var cert = certFactory.generateCertificate(certInputStream);
      if (cert instanceof X509Certificate x509Cert) {
        return x509Cert;
      }
      fail(
          "VAU channel certificate is not an X.509 certificate, got: %s".formatted(cert.getType()));
    } catch (IOException | CertificateException | NoSuchProviderException e) {
      fail("failed to parse VAU channel certificate", e);
    }
    return null;
  }
}
