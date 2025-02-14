package com.oviva.telematik.vau.epa4all.client.authz.internal;

import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.model.PinStatus;
import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;
import com.oviva.telematik.vau.epa4all.client.authz.RsaSignatureService;
import java.security.cert.X509Certificate;

public class RsaSignatureAdapter implements RsaSignatureService {

  private final KonnektorService konnektorService;
  private final SmcbCard card;

  public RsaSignatureAdapter(KonnektorService konnektorService, SmcbCard card) {
    this.konnektorService = konnektorService;
    this.card = card;
  }

  @Override
  public X509Certificate authCertificate() {
    return card.authRsaCertificate();
  }

  @Override
  public byte[] authSign(byte[] bytesToSign) {
    if (konnektorService.verifySmcPin(card.handle()) != PinStatus.VERIFIED) {
      throw new Epa4AllClientException(
          "PIN not verified: %s (%s)".formatted(card.holderName(), card.handle()));
    }
    return konnektorService.authSignRsaPss(card.handle(), bytesToSign);
  }
}
