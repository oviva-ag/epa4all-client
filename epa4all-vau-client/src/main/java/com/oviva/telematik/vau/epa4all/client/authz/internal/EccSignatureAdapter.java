package com.oviva.telematik.vau.epa4all.client.authz.internal;

import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.model.PinStatus;
import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.vau.epa4all.client.Epa4AllClientException;
import com.oviva.telematik.vau.epa4all.client.authz.SignatureService;
import java.security.cert.X509Certificate;

public class EccSignatureAdapter implements SignatureService {

  private final KonnektorService konnektorService;
  private final SmcbCard card;

  public EccSignatureAdapter(KonnektorService konnektorService, SmcbCard card) {
    this.konnektorService = konnektorService;
    this.card = card;
  }

  @Override
  public X509Certificate authCertificate() {
    return card.authEccCertificate();
  }

  @Override
  public byte[] authSign(byte[] bytesToSign) {
    if (konnektorService.verifySmcPin(card.handle()) != PinStatus.VERIFIED) {
      throw new Epa4AllClientException(
          "PIN not verified: %s (%s)".formatted(card.holderName(), card.handle()));
    }
    return konnektorService.authSignEcdsa(card.handle(), bytesToSign);
  }
}
