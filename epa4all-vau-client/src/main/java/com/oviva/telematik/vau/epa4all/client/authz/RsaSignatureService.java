package com.oviva.telematik.vau.epa4all.client.authz;

import java.security.cert.X509Certificate;

public interface RsaSignatureService {

  X509Certificate authCertificate();

  byte[] authSign(byte[] bytesToSign);
}
