package com.oviva.telematik.pkitrust;

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
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DownloadCaRoots {

  // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/latest/#5.1
  private static final String ROOTS_URL_TEST =
      "https://download-test.tsl.ti-dienste.de/ECC/ROOT-CA/roots.json";
  private static final String ROOTS_URL_PU =
      "https://download.tsl.ti-dienste.de/ECC/ROOT-CA/roots.json";
  private static final Logger log = LoggerFactory.getLogger(DownloadCaRoots.class);

  public void downloadTrustedCertificatesTEST(Path dst) throws CertificateException, IOException, NoSuchProviderException {

    var certs = downloadCertificates(ROOTS_URL_TEST);
    updateKeystore(dst, certs);
  }

  public void downloadTrustedCertificatesPU(Path dst) throws CertificateException, IOException, NoSuchProviderException {

    var certs = downloadCertificates(ROOTS_URL_PU);
    updateKeystore(dst, certs);
  }

  private List<X509Certificate> downloadCertificates(String rootsUri)
      throws IOException, CertificateException, NoSuchProviderException {

    var om = new ObjectMapper().registerModule(new JavaTimeModule());
    var roots =
        om.readValue(URI.create(rootsUri).toURL(), new TypeReference<List<CertRecord>>() {});

    var cf = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);

    return roots.stream()
        .filter(r -> r.nva().isAfter(Instant.now()))
        .map(
            r -> {
              try {
                var cert =
                    (X509Certificate)
                        cf.generateCertificate(
                            new ByteArrayInputStream(Base64.getDecoder().decode(r.cert())));
                log.atInfo().log(
                    "added root certificate {}", cert.getSubjectX500Principal().getName());
                return cert;
              } catch (CertificateException e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  private void updateKeystore(Path trustStorePath, List<X509Certificate> certificates) {
    var ts = createTruststore(certificates);
    saveTruststore(trustStorePath, ts);
  }

  @SuppressWarnings("java:S6437")
  private void saveTruststore(Path trustStorePath, KeyStore trustStore) {
    try (var fout =
        Files.newOutputStream(
            trustStorePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      trustStore.store(fout, "1234".toCharArray());
    } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      throw new IllegalStateException("failed to save truststore", e);
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
      throw new IllegalStateException(e);
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
