package com.oviva.telematik.vau.httpclient.internal.cert;

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.VauClientException;
import de.gematik.vau.lib.exceptions.VauProtocolException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.security.NoSuchProviderException;
import java.security.cert.*;
import java.util.*;
import java.util.stream.Collectors;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VauCertificateClient {

  private static final Logger log = LoggerFactory.getLogger(VauCertificateClient.class);

  private final TrustValidator trustValidator;
  private final CBORMapper mapper = new CBORMapper();
  private final HttpClient outerHttpClient;

  public VauCertificateClient(HttpClient outerHttpClient, TrustValidator trustValidator) {
    this.trustValidator = trustValidator;
    this.outerHttpClient = outerHttpClient;
  }

  public CertData fetchAndValidate(URI endpoint, byte[] certHash, int cdv, byte[] ocspResponseDer)
      throws CertificateValidationException {

    if (log.isDebugEnabled()) {
      log.atDebug().log("ocsp response:\n{}", Hex.toHexString(ocspResponseDer));
    }

    var data = fetch(endpoint, certHash, cdv);

    var ca = parseDerCertificate(data.ca());
    var cert = parseDerCertificate(data.cert());
    var chain = data.rcaChain().stream().map(VauCertificateClient::parseDerCertificate).toList();

    trustValidator.validate(cert, ca, chain, ocspResponseDer);
    return new CertData(cert, ca, chain);
  }

  private String certificateDerChainToPem(List<byte[]> certificates) {
    return certificates.stream().map(this::certificateDerToPem).collect(Collectors.joining());
  }

  private String certificateDerToPem(byte[] certificate) {

    try (var sw = new StringWriter()) {
      try (var w = new PemWriter(sw)) {
        var pemObject = new PemObject("CERTIFICATE", certificate);
        w.writeObject(pemObject);
        w.flush();
      }
      return sw.toString();
    } catch (IOException e) {
      throw new IllegalStateException("What the heck? Should not happen.", e);
    }
  }

  public CertDataResponse fetch(URI endpoint, byte[] certHash, int cdv) {
    var hex = HexFormat.of();
    var certIdentifier = "%s-%d".formatted(hex.formatHex(certHash), cdv);
    var certEndpoint = endpoint.resolve("/CertData.%s".formatted(certIdentifier));
    var res = outerHttpClient.call(new HttpClient.Request(certEndpoint, "GET", List.of(), null));
    if (res.status() != 200) {
      throw new VauClientException(
          "invalid code %d, expected 200: GET %s".formatted(res.status(), certEndpoint));
    }
    try {
      var data = mapper.readValue(res.body(), CertDataResponse.class);

      if (log.isDebugEnabled()) {
        log.atDebug().log("cert response:\n{}", certificateDerToPem(data.cert()));
        log.atDebug().log("ca response:\n{}", certificateDerToPem(data.ca()));
        log.atDebug().log("chain response:\n{}", certificateDerChainToPem(data.rcaChain()));
      }

      return data;
    } catch (IOException e) {
      throw new VauClientException("invalid response body: GET %s".formatted(certEndpoint), e);
    }
  }

  private static X509Certificate parseDerCertificate(byte[] certBytes) {
    try (var certInputStream = new ByteArrayInputStream(certBytes)) {
      // MUST be bouncycastle to deal with the brainpool certificates
      var certFactory = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
      var cert = certFactory.generateCertificate(certInputStream);
      if (cert instanceof X509Certificate x509Cert) {
        return x509Cert;
      }
      throw new VauProtocolException(
          "VAU channel certificate is not an X.509 certificate, got: %s".formatted(cert.getType()));
    } catch (IOException | CertificateException | NoSuchProviderException e) {
      throw new VauProtocolException("failed to parse VAU channel certificate", e);
    }
  }
}
