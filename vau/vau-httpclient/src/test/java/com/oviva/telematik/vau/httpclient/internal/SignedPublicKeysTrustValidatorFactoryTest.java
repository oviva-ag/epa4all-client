package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.internal.cert.TrustValidator;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignedPublicKeysTrustValidatorFactoryTest {

  private static final URI VAU_URI = URI.create("https://vau.example.com/VAU");

  @Mock private HttpClient outerClient;
  @Mock private TrustValidator trustValidator;

  @Test
  void create_returnsStateMachine_forPu() {
    var sut = new SignedPublicKeysTrustValidatorFactory(true, outerClient, trustValidator);
    var result = sut.create(VAU_URI);
    assertNotNull(result);
  }

  @Test
  void create_returnsStateMachine_forRu() {
    var sut = new SignedPublicKeysTrustValidatorFactory(false, outerClient, trustValidator);
    var result = sut.create(VAU_URI);
    assertNotNull(result);
  }
}
