package com.oviva.telematik.vau.epa4all.client.authz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.*;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BP256ECDHEncrypter;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BP256ECKey;
import com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolCurve;
import java.security.Security;
import java.text.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

class BPECKeyTest {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void parse() throws ParseException, JOSEException {

    var idpEncKey =
        BP256ECKey.parse(
            """
                    {
                      "kid": "puk_idp_enc",
                      "use": "enc",
                      "kty": "EC",
                      "crv": "BP-256",
                      "x": "pkU8LlTZsoGTloO7yjIkV626aGtwpelJ2Wrx7fZtOTo",
                      "y": "VliGWQLNtyGuQFs9nXbWdE9O9PFtxb42miy4yaCkCi8"
                    }
                """);

    var pub = idpEncKey.toECPublicKey(null);
    var encrypter = new BP256ECDHEncrypter(pub);
    encrypter.getJCAContext().setProvider(new BouncyCastleProvider());

    var cleartext = "Hello World!";
    var header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM).build();

    var jwe = new JWEObject(header, new Payload(cleartext));
    jwe.encrypt(encrypter);

    assertTrue(BrainpoolCurve.isBP256(pub.getParams()));

    System.out.println(jwe.serialize());
  }
}
