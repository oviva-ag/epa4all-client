package com.oviva.telematik.epa4all.client.internal;

import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import com.oviva.telematik.vau.epa4all.client.authz.internal.AuthnChallengeResponder;
import java.net.URI;
import java.util.List;

public class PinnedIdpTrustValidator implements AuthnChallengeResponder.IdpTrustValidator {

  private static final List<URI> TRUSTED_IDP_ISSUERS_PU =
      List.of(
          // A_19874-05 - Bereitstellung des internen Discovery Documents innerhalb der TI
          URI.create("https://idp.zentral.idp.splitdns.ti-dienste.de"),
          // A_19877-04 - Bereitstellung des externen Discovery Documents im Internet
          URI.create("https://idp.app.ti-dienste.de"));

  private static final List<URI> TRUSTED_IDP_ISSUERS_RU =
      List.of(
          // A_19874-05 - Bereitstellung des internen Discovery Documents innerhalb der TI
          URI.create("https://idp-ref.zentral.idp.splitdns.ti-dienste.de"),
          // A_19877-04 - Bereitstellung des externen Discovery Documents im Internet
          URI.create("https://idp-ref.app.ti-dienste.de"));

  private final List<URI> trustedIssuers;
  private final Environment env;

  public PinnedIdpTrustValidator(List<URI> trustedIssuers, Environment env) {
    this.trustedIssuers = trustedIssuers;
    this.env = env;
  }

  public static AuthnChallengeResponder.IdpTrustValidator forEnvironment(Environment env) {

    var trustedIssuers =
        switch (env) {
          case PU -> TRUSTED_IDP_ISSUERS_PU;
          case RU -> TRUSTED_IDP_ISSUERS_RU;
        };
    return new PinnedIdpTrustValidator(trustedIssuers, env);
  }

  @Override
  public void validate(URI issuer) throws AuthorizationException {

    if (!issuer.getScheme().equals("https")) {
      throw new AuthorizationException(
          "only https is allowed for idp trust validation, issuer was '%s'".formatted(issuer));
    }

    trustedIssuers.stream()
        .filter(
            trusted ->
                trusted.getHost().equals(issuer.getHost())
                    && trusted.getScheme().equals(issuer.getScheme())
                    && trusted.getPort() == issuer.getPort())
        .findFirst()
        .orElseThrow(
            () ->
                new AuthorizationException(
                    "issuer '%s' is not trusted in environment %s".formatted(issuer, env)));
  }
}
