package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import static com.oviva.telematik.vau.epa4all.client.authz.internal.jose.BrainpoolCurve.BP_256;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.*;
import com.nimbusds.jose.util.Base64;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Stream;
import org.bouncycastle.jce.ECNamedCurveTable;

public class BP256ECKey extends JWK implements AsymmetricJWK, CurveBasedJWK {

  private final Curve crv;
  private final Base64URL x;
  private final Base64URL y;

  public static BP256ECKey fromPublicKey(ECPublicKey pub) {

    var x =
        encodeCoordinate(
            pub.getParams().getCurve().getField().getFieldSize(), pub.getW().getAffineX());
    var y =
        encodeCoordinate(
            pub.getParams().getCurve().getField().getFieldSize(), pub.getW().getAffineY());

    return new BP256ECKey(BP_256, x, y, KeyType.EC, null, null, BrainpoolAlgorithms.BS256R1, null);
  }

  public static BP256ECKey parse(String s) throws ParseException {
    var jsonObject = JSONObjectUtils.parse(s);

    var kty = KeyType.parse(JSONObjectUtils.getString(jsonObject, JWKParameterNames.KEY_TYPE));
    if (!KeyType.EC.equals(kty)) {
      throw new ParseException(
          "The key type \"kty\" must be EC but was '%s'".formatted(kty.getValue()), 0);
    }

    var crvName = jsonObject.get(JWKParameterNames.ELLIPTIC_CURVE);
    if (!BP_256.getName().equals(crvName)) {
      throw new ParseException(
          "The curve \"crv\" must be BP-256 but was %s, nothing else supported"
              .formatted(jsonObject.get(JWKParameterNames.ELLIPTIC_CURVE)),
          0);
    }

    var x = JSONObjectUtils.getBase64URL(jsonObject, JWKParameterNames.ELLIPTIC_CURVE_X_COORDINATE);
    var y = JSONObjectUtils.getBase64URL(jsonObject, JWKParameterNames.ELLIPTIC_CURVE_Y_COORDINATE);

    // https://tools.ietf.org/html/rfc7517#section-4.7
    var chain =
        Stream.ofNullable(
                X509CertChainUtils.toBase64List(
                    JSONObjectUtils.getJSONArray(jsonObject, JWKParameterNames.X_509_CERT_CHAIN)))
            .flatMap(Collection::stream)
            .toList();
    if (chain.isEmpty()) {
      chain = null;
    }

    var use = KeyUse.parse(JSONObjectUtils.getString(jsonObject, JWKParameterNames.PUBLIC_KEY_USE));
    var ops =
        KeyOperation.parse(JSONObjectUtils.getStringList(jsonObject, JWKParameterNames.KEY_OPS));
    var alg = Algorithm.parse(JSONObjectUtils.getString(jsonObject, JWKParameterNames.ALGORITHM));

    return new BP256ECKey(BP_256, x, y, kty, use, ops, alg, chain);
  }

  private BP256ECKey(
      Curve crv,
      Base64URL x,
      Base64URL y,
      KeyType kty,
      KeyUse use,
      Set<KeyOperation> ops,
      Algorithm alg,
      List<Base64> x5c) {
    super(kty, use, ops, alg, null, null, null, null, x5c, null, null, null, null, null);

    this.crv = crv;
    this.x = Objects.requireNonNull(x, "The x coordinate must not be null");
    this.y = Objects.requireNonNull(y, "The y coordinate must not be null");

    //    ensurePublicCoordinatesOnCurve(crv, x, y);
    //    ensureMatches(getParsedX509CertChain());
  }

  public static Base64URL encodeCoordinate(final int fieldSize, final BigInteger coordinate) {

    final byte[] notPadded = BigIntegerUtils.toBytesUnsigned(coordinate);

    int bytesToOutput = (fieldSize + 7) / 8;

    if (notPadded.length >= bytesToOutput) {
      // Greater-than check to prevent exception on malformed
      // key below
      return Base64URL.encode(notPadded);
    }

    final byte[] padded = new byte[bytesToOutput];

    System.arraycopy(notPadded, 0, padded, bytesToOutput - notPadded.length, notPadded.length);

    return Base64URL.encode(padded);
  }

  @Override
  public Map<String, Object> toJSONObject() {

    Map<String, Object> o = super.toJSONObject();

    // Append EC specific attributes
    o.put(JWKParameterNames.ELLIPTIC_CURVE, crv.toString());
    o.put(JWKParameterNames.ELLIPTIC_CURVE_X_COORDINATE, x.toString());
    o.put(JWKParameterNames.ELLIPTIC_CURVE_Y_COORDINATE, y.toString());

    //    if (d != null) {
    //      o.put(JWKParameterNames.ELLIPTIC_CURVE_PRIVATE_KEY, d.toString());
    //    }

    return o;
  }

  @Override
  public PublicKey toPublicKey() throws JOSEException {
    return toECPublicKey(null);
  }

  @Override
  public PrivateKey toPrivateKey() throws JOSEException {
    throw new UnsupportedOperationException();
  }

  @Override
  public KeyPair toKeyPair() throws JOSEException {
    return new KeyPair(toPublicKey(), /* not supported yet*/ null);
  }

  @Override
  public boolean matches(X509Certificate cert) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Curve getCurve() {
    return crv;
  }

  @Override
  public LinkedHashMap<String, ?> getRequiredParams() {

    LinkedHashMap<String, String> requiredParams = new LinkedHashMap<>();
    requiredParams.put(JWKParameterNames.ELLIPTIC_CURVE, crv.toString());
    requiredParams.put(JWKParameterNames.KEY_TYPE, getKeyType().getValue());
    requiredParams.put(JWKParameterNames.ELLIPTIC_CURVE_X_COORDINATE, x.toString());
    requiredParams.put(JWKParameterNames.ELLIPTIC_CURVE_Y_COORDINATE, y.toString());
    return requiredParams;
  }

  @Override
  public boolean isPrivate() {
    return false;
  }

  @Override
  public JWK toPublicJWK() {
    return new BP256ECKey(
        crv,
        x,
        y,
        getKeyType(),
        getKeyUse(),
        getKeyOperations(),
        getAlgorithm(),
        getX509CertChain());
  }

  @Override
  public JWK toRevokedJWK(KeyRevocation keyRevocation) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    var spec = ECNamedCurveTable.getParameterSpec(getCurve().getStdName());
    return spec.getCurve().getFieldSize();
  }

  /**
   * Returns a standard {@code java.security.interfaces.ECPublicKey} representation of this Elliptic
   * Curve JWK.
   *
   * @param provider The JCA provider to use, {@code null} implies the default.
   * @return The public Elliptic Curve key.
   * @throws JOSEException If EC is not supported by the underlying Java Cryptography (JCA) provider
   *     or if the JWK parameters are invalid for a public EC key.
   */
  public ECPublicKey toECPublicKey(final Provider provider) throws JOSEException {

    var bcParameterSpec = ECNamedCurveTable.getParameterSpec(getCurve().getStdName());
    var bcCurveSpec = bcParameterSpec.getCurve();

    var spec =
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

    var w = new ECPoint(x.decodeToBigInteger(), y.decodeToBigInteger());

    var publicKeySpec = new ECPublicKeySpec(w, spec);

    try {
      KeyFactory keyFactory;

      if (provider == null) {
        keyFactory = KeyFactory.getInstance("EC");
      } else {
        keyFactory = KeyFactory.getInstance("EC", provider);
      }

      return (ECPublicKey) keyFactory.generatePublic(publicKeySpec);

    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {

      throw new JOSEException(e.getMessage(), e);
    }
  }
}
