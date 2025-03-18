package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import com.nimbusds.jose.jwk.Curve;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import org.bouncycastle.jce.ECNamedCurveTable;

public class BrainpoolCurve {
  private BrainpoolCurve() {}

  public static final Curve BP_256 = new Curve("BP-256", "brainpoolP256r1", null);

  public static final ECParameterSpec BP_256_SPEC;

  static {
    var bcParameterSpec = ECNamedCurveTable.getParameterSpec(BP_256.getStdName());
    var bcCurveSpec = bcParameterSpec.getCurve();

    BP_256_SPEC =
        new ECParameterSpec(
            new EllipticCurve(
                new ECFieldFp(bcCurveSpec.getField().getCharacteristic()),
                bcCurveSpec.getA().toBigInteger(),
                bcCurveSpec.getB().toBigInteger()),
            new ECPoint(
                bcParameterSpec.getG().getAffineXCoord().toBigInteger(),
                bcParameterSpec.getG().getAffineYCoord().toBigInteger()),
            bcParameterSpec.getN(),
            bcParameterSpec.getH().intValueExact());
  }

  public static boolean isBP256(ECParameterSpec spec) {

    return spec != null
        && spec.getCurve().getField().getFieldSize()
            == BP_256_SPEC.getCurve().getField().getFieldSize()
        && spec.getCurve().getA().equals(BP_256_SPEC.getCurve().getA())
        && spec.getCurve().getB().equals(BP_256_SPEC.getCurve().getB())
        && spec.getGenerator().getAffineX().equals(BP_256_SPEC.getGenerator().getAffineX())
        && spec.getGenerator().getAffineY().equals(BP_256_SPEC.getGenerator().getAffineY())
        && spec.getOrder().equals(BP_256_SPEC.getOrder())
        && spec.getCofactor() == BP_256_SPEC.getCofactor();
  }
}
