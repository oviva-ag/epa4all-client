package com.oviva.telematik.epa4all.client.internal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("used to update trust-roots")
class DownloadCaRootsTest {

  // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/latest/#5.1
  private static final String ROOTS_URL_TEST =
      "https://download-test.tsl.ti-dienste.de/ECC/ROOT-CA/roots.json";
  private static final String ROOTS_URL_PU =
      "https://download.tsl.ti-dienste.de/ECC/ROOT-CA/roots.json";

  @Test
  void downloadTrustedCertificatesTEST() throws Exception {

    var trustStorePath = Path.of("root-ca-test.p12");
    var certs = downloadCertificates(ROOTS_URL_TEST);
    updateKeystore(trustStorePath, certs);
  }

  @Test
  void downloadTrustedCertificatesPU() throws Exception {

    var trustStorePath = Path.of("root-ca-pu.p12");
    var certs = downloadCertificates(ROOTS_URL_PU);
    updateKeystore(trustStorePath, certs);
  }

  private List<X509Certificate> downloadCertificates(String rootsUri)
      throws IOException, CertificateException {

    var om = new ObjectMapper().registerModule(new JavaTimeModule());
    var roots =
        om.readValue(URI.create(rootsUri).toURL(), new TypeReference<List<CertRecord>>() {});

    var cf = CertificateFactory.getInstance("X.509");

    return roots.stream()
        .filter(r -> r.nva().isAfter(Instant.now()))
        .map(
            r -> {
              try {
                return (X509Certificate)
                    cf.generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode(r.cert())));
              } catch (CertificateException e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  private void updateKeystore(Path trustStorePath, List<X509Certificate> certificates) {
    var ts = createTruststore(certificates);
    saveTruststore(trustStorePath, ts);
  }

  private void saveTruststore(Path trustStorePath, KeyStore trustStore) {
    try (var fout =
        Files.newOutputStream(
            trustStorePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      trustStore.store(fout, "1234".toCharArray());
    } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      throw new RuntimeException("failed to save truststore", e);
    }
  }

  private KeyStore createTruststore(List<X509Certificate> certificates) {
    try {
      var trustStore = KeyStore.getInstance("PKCS12");
      trustStore.load(null, null);

      for (X509Certificate certificate : certificates) {
        trustStore.setCertificateEntry(
            certificate.getSubjectX500Principal().getName(), certificate);
      }

      return trustStore;
    } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
      throw new RuntimeException(e);
    }
  }

  record CertRecord(
      @JsonProperty String cert,
      @JsonProperty String cn,
      @JsonProperty String name,
      @JsonProperty String next,
      @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC") @JsonProperty Instant nva,
      @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC") @JsonProperty Instant nvb,
      @JsonProperty String prev,
      @JsonProperty String ski) {}
}
