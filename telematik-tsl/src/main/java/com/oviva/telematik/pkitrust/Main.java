package com.oviva.telematik.pkitrust;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Enumeration;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  /** Downloads the root and TSL certificates for the Telematik-Infrastruktur */
  public static void main(String[] args) throws Exception {

    var roots = new DownloadCaRoots();
    var tsl = new DownloadTsl();

    var clientBasePath = Path.of("epa4all-client/src/main/resources");

    var tmpDir = Files.createTempDirectory("telematik-certs");

    var pathPuRoots = tmpDir.resolve("root-ca-pu.p12");
    var pathPuTsl = tmpDir.resolve("tsl-certificates-pu.p12");
    roots.downloadTrustedCertificatesPU(pathPuRoots);
    tsl.downloadTrustServiceListPU(pathPuTsl);

    mergeKeystoresTo(clientBasePath.resolve("truststore-pu.p12"), pathPuTsl, pathPuRoots);

    var pathTestRoots = tmpDir.resolve("root-ca-test.p12");
    var pathTestTsl = tmpDir.resolve("tsl-certificates-test.p12");
    roots.downloadTrustedCertificatesTEST(pathTestRoots);
    tsl.downloadTrustServiceListTEST(pathTestTsl);

    mergeKeystoresTo(clientBasePath.resolve("truststore-test.p12"), pathTestTsl, pathTestRoots);
  }

  public static void mergeKeystoresTo(Path dest, Path... src) {

    try (var fout =
        Files.newOutputStream(
            dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      var ks = KeyStore.getInstance("PKCS12");
      ks.load(null, null);
      for (var s : src) {
        try (var fin = Files.newInputStream(s)) {
          var sks = KeyStore.getInstance("PKCS12");
          sks.load(fin, "1234".toCharArray());
          mergeKeystores(sks, ks);
        }
      }
      ks.store(fout, "1234".toCharArray());
    } catch (CertificateException | IOException | NoSuchAlgorithmException | KeyStoreException e) {
      throw new RuntimeException(e);
    }
  }

  public static void mergeKeystores(KeyStore sourceKeystore, KeyStore destKeystore)
      throws KeyStoreException {
    Enumeration<String> aliases = sourceKeystore.aliases();

    while (aliases.hasMoreElements()) {
      var alias = aliases.nextElement();
      if (sourceKeystore.isCertificateEntry(alias)) {
        var cert = sourceKeystore.getCertificate(alias);
        destKeystore.setCertificateEntry(alias, cert);
      }
    }
  }
}
