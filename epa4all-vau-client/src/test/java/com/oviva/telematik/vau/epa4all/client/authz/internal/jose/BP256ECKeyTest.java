package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKParameterNames;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BP256ECKeyTest {

  @BeforeAll
  static void setUp() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void testSerializationFullLifecycleSuccess() throws Exception {
    // Given
    var keyPair = BrainpoolKeyGenerator.generateBP256KeyPair();
    var originalPublicKey = (ECPublicKey) keyPair.getPublic();

    // When
    var bp256Key = BP256ECKey.fromPublicKey(originalPublicKey);

    // Then
    assertNotNull(bp256Key);
    assertTrue(BrainpoolCurve.isBP256(originalPublicKey.getParams()));

    // When
    var jsonObject = bp256Key.toJSONObject();
    var jsonString = JSONObjectUtils.toJSONString(jsonObject);

    // Then
    assertNotNull(jsonString);
    assertEquals(BrainpoolCurve.BP_256.getName(), jsonObject.get(JWKParameterNames.ELLIPTIC_CURVE));

    // When
    var parsedKey = BP256ECKey.parse(jsonString);
    var reconstructedPublicKey = parsedKey.toECPublicKey(null);

    // Then
    assertNotNull(parsedKey);
    assertNotNull(reconstructedPublicKey);

    // Verify key coordinates are preserved
    assertEquals(originalPublicKey.getW().getAffineX(), reconstructedPublicKey.getW().getAffineX());
    assertEquals(originalPublicKey.getW().getAffineY(), reconstructedPublicKey.getW().getAffineY());
    assertTrue(BrainpoolCurve.isBP256(reconstructedPublicKey.getParams()));
  }

  @Test
  void testParse() throws ParseException, JOSEException {
    // Given
    var keyJson =
        """
        {
          "kid": "puk_idp_enc",
          "use": "enc",
          "kty": "EC",
          "crv": "BP-256",
          "x": "pkU8LlTZsoGTloO7yjIkV626aGtwpelJ2Wrx7fZtOTo",
          "y": "VliGWQLNtyGuQFs9nXbWdE9O9PFtxb42miy4yaCkCi8"
        }
        """;

    // When
    var bpKey = BP256ECKey.parse(keyJson);
    var publicKey = bpKey.toECPublicKey(null);

    // Then
    assertNotNull(bpKey);
    assertNotNull(publicKey);
    assertTrue(BrainpoolCurve.isBP256(publicKey.getParams()));
  }
}
