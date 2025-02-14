package com.oviva.telematik.vau.httpclient.internal.cert;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.jupiter.api.Test;

class TrustStoreValidatorTest {

  static {
    Security.addProvider(new BouncyCastlePQCProvider());
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void validate() throws CertificateValidationException {
    var cert = loadPemCertificates("src/test/resources/cert.pem");
    var chain = loadPemCertificates("src/test/resources/chain.pem");
    var ca = loadPemCertificates("src/test/resources/ca.pem");
    var roots = loadRUTrustStore();
    var ocspDer = loadOcspHex("src/test/resources/ocsp.hex");

    TrustStoreValidator.clock =
        Clock.fixed(Instant.parse("2025-03-13T10:56:18Z"), ZoneId.systemDefault());
    var validator = new TrustStoreValidator(roots);

    validator.validate(cert.get(0), ca.get(0), chain, ocspDer);
  }

  private byte[] loadOcspHex(String path) {
    try {
      return Hex.decode(Files.readAllBytes(Path.of(path)));
    } catch (IOException e) {
      fail(e);
      return new byte[0];
    }
  }

  private KeyStore loadRUTrustStore() {

    return assertDoesNotThrow(
        () -> {
          var trustStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
          trustStore.load(
              this.getClass().getResourceAsStream("/truststore-test.p12"), "1234".toCharArray());
          assertTrue(trustStore.size() > 0);
          return trustStore;
        });
  }

  private List<X509Certificate> loadPemCertificates(String path) {

    try (var reader = new PemReader(Files.newBufferedReader(Path.of(path)))) {
      var certificateFactory =
          CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);

      var objects = new ArrayList<PemObject>();
      var po = reader.readPemObject();
      while (po != null) {
        objects.add(po);
        po = reader.readPemObject();
      }

      return objects.stream()
          .map(
              p -> {
                if (!p.getType().equals("CERTIFICATE")) {
                  throw new RuntimeException("expected certificate, but got " + p.getType());
                }
                return p;
              })
          .map(
              p -> {
                try {
                  return (X509Certificate)
                      certificateFactory.generateCertificate(
                          new ByteArrayInputStream(p.getContent()));
                } catch (CertificateException e) {
                  throw new RuntimeException(e);
                }
              })
          .toList();
    } catch (CertificateException | IOException | NoSuchProviderException e) {
      fail(e);
      return List.of();
    }
  }
}
