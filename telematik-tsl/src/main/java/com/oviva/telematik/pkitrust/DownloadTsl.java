package com.oviva.telematik.pkitrust;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DownloadTsl {

  private static final Logger logger = LoggerFactory.getLogger(DownloadTsl.class);

  // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TSL/gemSpec_TSL_V1.21.0/#A_17680-01

  // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Krypt/latest/#5.1
  private static final String TSL_URL_TEST =
      "https://download-test.tsl.ti-dienste.de/ECC/ECC-RSA_TSL-test.xml";
  private static final String TSL_URL_PU = "https://download.tsl.ti-dienste.de/ECC/ECC-RSA_TSL.xml";

  public static void main(String[] args) {
    new DownloadTsl().downloadTrustServiceList();
  }

  public void downloadTrustServiceList() {
    downloadTrustServiceListPU(Path.of("tsl-certificates-pu.p12"));
    downloadTrustServiceListTEST(Path.of("tsl-certificates-test.p12"));
  }

  public void downloadTrustServiceListPU(Path p) {

    try {
      var certs = downloadTsl(TSL_URL_PU);
      updateKeystore(p, certs);
    } catch (IOException e) {
      throw new IllegalStateException("failed to download tsl", e);
    }
  }

  public void downloadTrustServiceListTEST(Path p) {

    try {
      var certs = downloadTsl(TSL_URL_TEST);
      updateKeystore(p, certs);
    } catch (IOException e) {
      throw new IllegalStateException("failed to download tsl", e);
    }
  }

  private List<X509Certificate> downloadTsl(String rootsUri) throws IOException {

    var xm = new XmlMapper();

    var tree = xm.readTree(URI.create(rootsUri).toURL());

    var tsps = tree.path("TrustServiceProviderList").path("TrustServiceProvider");

    List<X509Certificate> certificates = new ArrayList<>();
    for (var tsp : tsps) {
      var tspServices = tsp.path("TSPServices").path("TSPService");
      for (var tspService : tspServices) {
        // ServiceInformation/ServiceDigitalIdentity/DigitalId/X509Certificate
        var serviceInformation = tspService.path("ServiceInformation");

        var name = serviceInformation.path("ServiceName").path("Name").path("").asText();
        logger.atInfo().log("adding {}", name);

        var sdis = serviceInformation.path("ServiceDigitalIdentity");

        if (sdis.size() > 1) {
          logger.atWarn().log("found more than one digital identity for {}", name);
        }

        var x509b64 = sdis.path("DigitalId").path("X509Certificate").asText();
        if (x509b64 != null && !x509b64.isBlank()) {
          var cert = parseBase64Certificate(x509b64);
          certificates.add(cert);
        } else {
          logger
              .atInfo()
              .addKeyValue("identity", sdis.toString())
              .log("found a non X509 identity for {}, skipping", name);
        }
      }
    }

    return certificates;
  }

  private X509Certificate parseBase64Certificate(String cert) {
    try {
      // IMPORTANT: Needs to be BouncyCastleProvider to deal with the German ECC curves (brainpool).
      var cf = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);

      return (X509Certificate)
          cf.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(cert)));
    } catch (CertificateException | NoSuchProviderException e) {
      throw new IllegalStateException(e);
    }
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
}
