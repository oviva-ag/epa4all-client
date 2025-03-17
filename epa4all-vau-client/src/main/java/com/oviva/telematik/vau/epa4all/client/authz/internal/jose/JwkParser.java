package com.oviva.telematik.vau.epa4all.client.authz.internal.jose;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKParameterNames;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import java.text.ParseException;

public class JwkParser {

  public static JWK parseJwk(String content) {
    try {
      var obj = JSONObjectUtils.parse(content);

      // special handling for brainpool curves
      var ktyString = JSONObjectUtils.getString(obj, JWKParameterNames.KEY_TYPE);
      var kty = KeyType.parse(ktyString);
      var crvString = JSONObjectUtils.getString(obj, JWKParameterNames.ELLIPTIC_CURVE);
      if (kty == KeyType.EC && BrainpoolCurve.BP_256.getName().equals(crvString)) {
        return BP256ECKey.parse(content);
      }

      return JWK.parse(content);
    } catch (ParseException e) {
      throw new AuthorizationException("Failed to parse JWK. Invalid JSON", e);
    }
  }
}
