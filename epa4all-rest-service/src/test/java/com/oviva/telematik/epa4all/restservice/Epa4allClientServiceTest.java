package com.oviva.telematik.epa4all.restservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oviva.epa.client.KonnektorService;
import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.epa4all.client.Epa4AllClient;
import com.oviva.telematik.epa4all.client.Epa4AllClientFactoryBuilder;
import de.gematik.epa.ihe.model.simple.AuthorInstitution;
import java.net.InetSocketAddress;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Epa4allClientServiceTest {

  private Epa4allClientService.KonnektorServiceFactory konnektorFactory;

  private InetSocketAddress proxy;
  private Environment environment;
  private String telematikId;

  @BeforeEach
  void setup() {
    konnektorFactory = mock(Epa4allClientService.KonnektorServiceFactory.class);
    proxy = new InetSocketAddress("127.0.0.1", 3128);
    environment = Environment.RU;
    telematikId = "test-telematik-id";
  }

  @Test
  void isHealthy_shouldReturnTrueWhenAuthorInstitutionSucceeds() {
    var konnektor = mock(KonnektorService.class);
    var client = mock(Epa4AllClient.class);

    when(konnektorFactory.get()).thenReturn(konnektor);
    when(client.authorInstitution()).thenReturn(new AuthorInstitution("name", "id"));

    try (MockedStatic<Epa4AllClientFactoryBuilder> builderStatic =
        Mockito.mockStatic(Epa4AllClientFactoryBuilder.class)) {

      var builder = mock(Epa4AllClientFactoryBuilder.class);
      var factory = mock(com.oviva.telematik.epa4all.client.internal.Epa4AllClientFactory.class);
      builderStatic.when(Epa4AllClientFactoryBuilder::newBuilder).thenReturn(builder);

      when(builder.konnektorProxyAddress(proxy)).thenReturn(builder);
      when(builder.konnektorService(konnektor)).thenReturn(builder);
      when(builder.environment(environment)).thenReturn(builder);
      when(builder.telematikId(telematikId)).thenReturn(builder);
      when(builder.build()).thenReturn(factory);

      when(factory.newClient()).thenReturn(client);

      var service = new Epa4allClientService(konnektorFactory, proxy, environment, telematikId);

      assertTrue(service.isHealthy());
    }
  }

  @Test
  void isHealthy_shouldReturnFalseWhenExceptionThrown() {
    var konnektor = mock(KonnektorService.class);
    var client = mock(Epa4AllClient.class);

    when(konnektorFactory.get()).thenReturn(konnektor);
    when(client.authorInstitution()).thenThrow(new RuntimeException("boom"));

    try (MockedStatic<Epa4AllClientFactoryBuilder> builderStatic =
        Mockito.mockStatic(Epa4AllClientFactoryBuilder.class)) {

      var builder = mock(Epa4AllClientFactoryBuilder.class);
      var factory = mock(com.oviva.telematik.epa4all.client.internal.Epa4AllClientFactory.class);
      builderStatic.when(Epa4AllClientFactoryBuilder::newBuilder).thenReturn(builder);

      when(builder.konnektorProxyAddress(proxy)).thenReturn(builder);
      when(builder.konnektorService(konnektor)).thenReturn(builder);
      when(builder.environment(environment)).thenReturn(builder);
      when(builder.telematikId(telematikId)).thenReturn(builder);
      when(builder.build()).thenReturn(factory);

      when(factory.newClient()).thenReturn(client);

      var service = new Epa4allClientService(konnektorFactory, proxy, environment, telematikId);

      assertFalse(service.isHealthy());
    }
  }

  @Test
  void writeDocument_shouldBuildAndCallClient() {
    var konnektor = mock(KonnektorService.class);
    var client = mock(Epa4AllClient.class);
    when(konnektorFactory.get()).thenReturn(konnektor);

    var author = new AuthorInstitution("name", "id");
    when(client.authorInstitution()).thenReturn(author);

    try (MockedStatic<Epa4AllClientFactoryBuilder> builderStatic =
        Mockito.mockStatic(Epa4AllClientFactoryBuilder.class)) {
      var builder = mock(Epa4AllClientFactoryBuilder.class);
      var factory = mock(com.oviva.telematik.epa4all.client.internal.Epa4AllClientFactory.class);
      builderStatic.when(Epa4AllClientFactoryBuilder::newBuilder).thenReturn(builder);

      when(builder.konnektorProxyAddress(proxy)).thenReturn(builder);
      when(builder.konnektorService(konnektor)).thenReturn(builder);
      when(builder.environment(environment)).thenReturn(builder);
      when(builder.telematikId(telematikId)).thenReturn(builder);
      when(builder.build()).thenReturn(factory);
      when(factory.newClient()).thenReturn(client);

      var service = new Epa4allClientService(konnektorFactory, proxy, environment, telematikId);

      var kvnr = "X123456789";
      var mime = "text/plain";
      var data = "hello".getBytes();

      var response = service.writeDocument(kvnr, mime, data);

      assertNotNull(response);
      assertNotNull(response.documentId());

      verify(client).writeDocument(eq(kvnr), any());
    }
  }

  @Test
  void replaceDocument_shouldBuildAndCallClient() {
    var konnektor = mock(KonnektorService.class);
    var client = mock(Epa4AllClient.class);
    when(konnektorFactory.get()).thenReturn(konnektor);

    var author = new AuthorInstitution("name", "id");
    when(client.authorInstitution()).thenReturn(author);

    try (MockedStatic<Epa4AllClientFactoryBuilder> builderStatic =
        Mockito.mockStatic(Epa4AllClientFactoryBuilder.class)) {
      var builder = mock(Epa4AllClientFactoryBuilder.class);
      var factory = mock(com.oviva.telematik.epa4all.client.internal.Epa4AllClientFactory.class);
      builderStatic.when(Epa4AllClientFactoryBuilder::newBuilder).thenReturn(builder);

      when(builder.konnektorProxyAddress(proxy)).thenReturn(builder);
      when(builder.konnektorService(konnektor)).thenReturn(builder);
      when(builder.environment(environment)).thenReturn(builder);
      when(builder.telematikId(telematikId)).thenReturn(builder);
      when(builder.build()).thenReturn(factory);
      when(factory.newClient()).thenReturn(client);

      var service = new Epa4allClientService(konnektorFactory, proxy, environment, telematikId);

      var kvnr = "X123456789";
      var mime = "text/plain";
      var data = "hello".getBytes();
      var toReplace = UUID.randomUUID();

      var response = service.replaceDocument(kvnr, mime, data, toReplace);

      assertNotNull(response);
      assertNotNull(response.documentId());

      verify(client).replaceDocument(eq(kvnr), any(), eq(toReplace));
    }
  }

  @Test
  void writeDocument_shouldValidateInputs() {
    var service = new Epa4allClientService(konnektorFactory, proxy, environment, telematikId);

    assertThrows(BadRequestException.class, () -> service.writeDocument(null, "m", new byte[0]));
    assertThrows(BadRequestException.class, () -> service.writeDocument("kvnr", null, new byte[0]));
    assertThrows(BadRequestException.class, () -> service.writeDocument("kvnr", "m", null));
  }

  @Test
  void replaceDocument_shouldValidateInputs() {
    var service = new Epa4allClientService(konnektorFactory, proxy, environment, telematikId);

    assertThrows(
        BadRequestException.class,
        () -> service.replaceDocument(null, "m", new byte[0], UUID.randomUUID()));
    assertThrows(
        BadRequestException.class,
        () -> service.replaceDocument("kvnr", null, new byte[0], UUID.randomUUID()));
    assertThrows(
        BadRequestException.class,
        () -> service.replaceDocument("kvnr", "m", null, UUID.randomUUID()));
    assertThrows(
        BadRequestException.class, () -> service.replaceDocument("kvnr", "m", new byte[0], null));
  }
}
