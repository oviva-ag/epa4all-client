package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class PinnedIdpTrustValidatorTest {

  @Test
  void forEnvironment_pu_acceptsPuIssuers() {
    var sut = PinnedIdpTrustValidator.forEnvironment(Environment.PU);
    assertDoesNotThrow(
        () -> sut.validate(URI.create("https://idp.zentral.idp.splitdns.ti-dienste.de")));
    assertDoesNotThrow(() -> sut.validate(URI.create("https://idp.app.ti-dienste.de")));
  }

  @Test
  void forEnvironment_ru_acceptsRuIssuers() {
    var sut = PinnedIdpTrustValidator.forEnvironment(Environment.RU);
    assertDoesNotThrow(
        () -> sut.validate(URI.create("https://idp-ref.zentral.idp.splitdns.ti-dienste.de")));
    assertDoesNotThrow(() -> sut.validate(URI.create("https://idp-ref.app.ti-dienste.de")));
  }

  @Test
  void forEnvironment_pu_rejectsRuIssuer() {
    var sut = PinnedIdpTrustValidator.forEnvironment(Environment.PU);
    assertThrows(
        AuthorizationException.class,
        () -> sut.validate(URI.create("https://idp-ref.zentral.idp.splitdns.ti-dienste.de")));
  }

  @Test
  void forEnvironment_ru_rejectsPuIssuer() {
    var sut = PinnedIdpTrustValidator.forEnvironment(Environment.RU);
    assertThrows(
        AuthorizationException.class,
        () -> sut.validate(URI.create("https://idp.zentral.idp.splitdns.ti-dienste.de")));
  }

  @Test
  void validate_rejectsNonHttpsScheme() {
    var sut =
        new PinnedIdpTrustValidator(List.of(URI.create("https://idp.example.com")), Environment.PU);
    assertThrows(
        AuthorizationException.class, () -> sut.validate(URI.create("http://idp.example.com")));
  }

  @Test
  void validate_rejectsUnknownIssuer() {
    var sut =
        new PinnedIdpTrustValidator(List.of(URI.create("https://idp.example.com")), Environment.PU);
    assertThrows(
        AuthorizationException.class, () -> sut.validate(URI.create("https://evil.example.com")));
  }

  @Test
  void validate_rejectsCorrectHostOnWrongPort() {
    var sut =
        new PinnedIdpTrustValidator(List.of(URI.create("https://idp.example.com")), Environment.PU);
    assertThrows(
        AuthorizationException.class,
        () -> sut.validate(URI.create("https://idp.example.com:8443")));
  }
}
