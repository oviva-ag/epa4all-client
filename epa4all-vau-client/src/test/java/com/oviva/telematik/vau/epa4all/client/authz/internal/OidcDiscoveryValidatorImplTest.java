package com.oviva.telematik.vau.epa4all.client.authz.internal;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolAlgorithms;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.bouncycastle.asn1.isismtt.x509.Admissions;
import org.bouncycastle.asn1.isismtt.x509.ProfessionInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OidcDiscoveryValidatorImplTest {

  private static final ASN1ObjectIdentifier IDP_DIENST_OID =
      new ASN1ObjectIdentifier("1.2.276.0.76.4.260");
  private static final ASN1ObjectIdentifier UNRELATED_OID =
      new ASN1ObjectIdentifier("1.2.276.0.76.4.999");

  @BeforeAll
  static void setUp() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void validate_validDiscoveryJwt_succeeds() throws Exception {
    var caKeyPair = generateKeyPair();
    var eeKeyPair = generateKeyPair();
    var caCert = buildCaCert(caKeyPair);
    var eeCert = buildEndEntityCert(eeKeyPair, caKeyPair, caCert, IDP_DIENST_OID);
    var trustStore = buildTrustStore(caCert);

    var jwt = buildDiscoveryJwt(eeKeyPair, eeCert);
    var validator = new OidcDiscoveryValidatorImpl(trustStore);

    assertDoesNotThrow(() -> validator.validate(jwt));
  }

  @Test
  void validate_untrustedCertificate_throwsAuthorizationException() throws Exception {
    var eeKeyPair = generateKeyPair();
    var selfSignedCert = buildSelfSignedCert(eeKeyPair, IDP_DIENST_OID);
    var emptyTrustStore = buildTrustStore();

    var jwt = buildDiscoveryJwt(eeKeyPair, selfSignedCert);
    var validator = new OidcDiscoveryValidatorImpl(emptyTrustStore);

    assertThrows(AuthorizationException.class, () -> validator.validate(jwt));
  }

  @Test
  void validate_wrongProfessionOid_throwsAuthorizationException() throws Exception {
    var caKeyPair = generateKeyPair();
    var eeKeyPair = generateKeyPair();
    var caCert = buildCaCert(caKeyPair);
    var eeCert = buildEndEntityCert(eeKeyPair, caKeyPair, caCert, UNRELATED_OID);
    var trustStore = buildTrustStore(caCert);

    var jwt = buildDiscoveryJwt(eeKeyPair, eeCert);
    var validator = new OidcDiscoveryValidatorImpl(trustStore);

    assertThrows(AuthorizationException.class, () -> validator.validate(jwt));
  }

  @Test
  void validate_badSignature_throwsAuthorizationException() throws Exception {
    var caKeyPair = generateKeyPair();
    var eeKeyPair = generateKeyPair();
    var wrongKeyPair = generateKeyPair();
    var caCert = buildCaCert(caKeyPair);
    var eeCert = buildEndEntityCert(eeKeyPair, caKeyPair, caCert, IDP_DIENST_OID);
    var trustStore = buildTrustStore(caCert);

    // signed with a key that doesn't match the cert's public key
    var jwt = buildDiscoveryJwt(wrongKeyPair, eeCert);
    var validator = new OidcDiscoveryValidatorImpl(trustStore);

    assertThrows(AuthorizationException.class, () -> validator.validate(jwt));
  }

  @Test
  void validate_missingRequiredClaims_throwsAuthorizationException() throws Exception {
    var caKeyPair = generateKeyPair();
    var eeKeyPair = generateKeyPair();
    var caCert = buildCaCert(caKeyPair);
    var eeCert = buildEndEntityCert(eeKeyPair, caKeyPair, caCert, IDP_DIENST_OID);
    var trustStore = buildTrustStore(caCert);

    var claims = new JWTClaimsSet.Builder().build(); // no required claims
    var jwt = buildSignedJwt(eeKeyPair, eeCert, claims);
    var validator = new OidcDiscoveryValidatorImpl(trustStore);

    assertThrows(AuthorizationException.class, () -> validator.validate(jwt));
  }

  // -- helpers --

  private static KeyPair generateKeyPair() throws Exception {
    var kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
    kpg.initialize(new ECGenParameterSpec("brainpoolP256r1"));
    return kpg.generateKeyPair();
  }

  private static KeyStore buildTrustStore(X509Certificate... trustedCerts) throws Exception {
    var ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(null, null);
    for (int i = 0; i < trustedCerts.length; i++) {
      ks.setCertificateEntry("ca-" + i, trustedCerts[i]);
    }
    return ks;
  }

  private static X509Certificate buildCaCert(KeyPair keyPair) throws Exception {
    var now = Instant.now();
    var name = new X500Name("CN=Test-CA");
    var certBuilder =
        new JcaX509v3CertificateBuilder(
            name,
            BigInteger.ONE,
            Date.from(now.minusSeconds(60)),
            Date.from(now.plusSeconds(3600)),
            name,
            keyPair.getPublic());
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    var signer =
        new JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.getPrivate());
    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(certBuilder.build(signer));
  }

  private static X509Certificate buildSelfSignedCert(
      KeyPair keyPair, ASN1ObjectIdentifier professionOid) throws Exception {
    var now = Instant.now();
    var name = new X500Name("CN=Test-IDP");
    var certBuilder =
        new JcaX509v3CertificateBuilder(
            name,
            BigInteger.ONE,
            Date.from(now.minusSeconds(60)),
            Date.from(now.plusSeconds(3600)),
            name,
            keyPair.getPublic());
    addAdmissionExtension(certBuilder, professionOid);
    var signer =
        new JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.getPrivate());
    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(certBuilder.build(signer));
  }

  private static X509Certificate buildEndEntityCert(
      KeyPair eeKeyPair,
      KeyPair caKeyPair,
      X509Certificate caCert,
      ASN1ObjectIdentifier professionOid)
      throws Exception {
    var now = Instant.now();
    var certBuilder =
        new JcaX509v3CertificateBuilder(
            caCert,
            BigInteger.TWO,
            Date.from(now.minusSeconds(60)),
            Date.from(now.plusSeconds(3600)),
            new javax.security.auth.x500.X500Principal("CN=Test-IDP"),
            eeKeyPair.getPublic());
    addAdmissionExtension(certBuilder, professionOid);
    var signer =
        new JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(caKeyPair.getPrivate());
    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(certBuilder.build(signer));
  }

  private static void addAdmissionExtension(
      JcaX509v3CertificateBuilder certBuilder, ASN1ObjectIdentifier professionOid)
      throws Exception {
    var profInfo =
        new ProfessionInfo(
            null,
            new org.bouncycastle.asn1.x500.DirectoryString[0],
            new ASN1ObjectIdentifier[] {professionOid},
            null,
            null);
    var admissions = new Admissions(null, null, new ProfessionInfo[] {profInfo});
    var admissionSyntax = new AdmissionSyntax(null, new DERSequence(admissions));
    certBuilder.addExtension(
        ISISMTTObjectIdentifiers.id_isismtt_at_admission, false, admissionSyntax);
  }

  private static SignedJWT buildDiscoveryJwt(KeyPair signingKeyPair, X509Certificate signingCert)
      throws Exception {
    var now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("issuer", "https://idp.example.test")
            .claim("uri_puk_idp_enc", "https://idp.example.test/enc")
            .claim("uri_puk_idp_sig", "https://idp.example.test/sig")
            .claim("jwks_uri", "https://idp.example.test/jwks.json")
            .build();
    return buildSignedJwt(signingKeyPair, signingCert, claims);
  }

  private static SignedJWT buildSignedJwt(
      KeyPair keyPair, X509Certificate cert, JWTClaimsSet claims) throws Exception {
    var x5c = List.of(Base64.encode(cert.getEncoded()));
    var header = new JWSHeader.Builder(BrainpoolAlgorithms.BS256R1).x509CertChain(x5c).build();
    var jwt = new SignedJWT(header, claims);

    var sig = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
    sig.initSign((java.security.interfaces.ECPrivateKey) keyPair.getPrivate());
    sig.update(jwt.getSigningInput());
    var jwsSignature = ECDSA.transcodeSignatureToConcat(sig.sign(), 64);

    return SignedJWT.parse(
        new SignedJWT(
                jwt.getHeader().toBase64URL(),
                jwt.getPayload().toBase64URL(),
                Base64URL.encode(jwsSignature))
            .serialize());
  }
}
