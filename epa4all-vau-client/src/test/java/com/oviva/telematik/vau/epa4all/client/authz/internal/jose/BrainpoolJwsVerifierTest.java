package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BrainpoolJwsVerifierTest {

  private static final String TEST_PAYLOAD = "Hello, Brainpool!";

  @BeforeAll
  static void setUp() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void testSupportedAlgorithms() {
    // Given
    var keyPair = BrainpoolKeyGenerator.generateBP256KeyPair();
    var publicKey = (ECPublicKey) keyPair.getPublic();
    var verifier = new BrainpoolJwsVerifier(publicKey);

    // When
    var supportedAlgorithms = verifier.supportedJWSAlgorithms();

    // Then
    assertEquals(Set.of(BrainpoolAlgorithms.BS256R1), supportedAlgorithms);
  }

  @Test
  void testUnsupportedAlgorithm() throws Exception {
    // Given
    var keyPair = BrainpoolKeyGenerator.generateBP256KeyPair();
    var publicKey = (ECPublicKey) keyPair.getPublic();
    var verifier = new BrainpoolJwsVerifier(publicKey);

    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
    var signingInput = createSigningInput(header, TEST_PAYLOAD);
    var signature = Base64URL.encode(new byte[] {1, 2, 3, 4, 5});

    // When/Then
    assertThrows(JOSEException.class, () -> verifier.verify(header, signingInput, signature));
  }

  @Test
  void testFactory() throws Exception {
    // Given
    var keyPair = BrainpoolKeyGenerator.generateBP256KeyPair();
    var publicKey = (ECPublicKey) keyPair.getPublic();
    var factory = new BrainpoolJwsVerifier.Factory();
    var header = new JWSHeader.Builder(BrainpoolAlgorithms.BS256R1).build();

    // When
    var verifier = factory.createJWSVerifier(header, publicKey);

    // Then
    assertNotNull(verifier);
    assertTrue(verifier instanceof BrainpoolJwsVerifier);
    assertEquals(Set.of(BrainpoolAlgorithms.BS256R1), factory.supportedJWSAlgorithms());
  }

  @Test
  void testRejectInvalidSignature() throws Exception {
    // Given
    var keyPair = BrainpoolKeyGenerator.generateBP256KeyPair();
    var publicKey = (ECPublicKey) keyPair.getPublic();
    var verifier = new BrainpoolJwsVerifier(publicKey);

    var header = new JWSHeader.Builder(BrainpoolAlgorithms.BS256R1).build();
    var signingInput = createSigningInput(header, TEST_PAYLOAD);

    // Create an invalid signature (just random bytes)
    var invalidSignature = Base64URL.encode(new byte[] {1, 2, 3, 4, 5});

    // When
    var isValid = verifier.verify(header, signingInput, invalidSignature);

    // Then
    assertFalse(isValid);
  }

  private byte[] createSigningInput(JWSHeader header, String payload) {
    var encodedHeader = header.toBase64URL();
    var encodedPayload = Base64URL.encode(payload);
    return (encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII);
  }
}
