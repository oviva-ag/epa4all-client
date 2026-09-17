package com.oviva.telematik.vau.epa4all.client.info;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InformationServiceTest {

  private static final String INSURANT_ID = "X123456789";

  @Mock HttpClient httpClient;

  @Mock HttpResponse<Void> httpResponse;

  @Test
  void findAccountEndpoint_returnsEndpoint_whenAccountFound() throws Exception {
    when(httpResponse.statusCode()).thenReturn(204);
    doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isPresent());
    assertEquals(URI.create("https://epa-as-1.dev.epa4all.de"), result.get());
  }

  @Test
  void findAccountEndpoint_returnsEmpty_whenNoActiveAccount() throws Exception {
    when(httpResponse.statusCode()).thenReturn(404);
    doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isEmpty());
  }

  @Test
  void findAccountEndpoint_returnsEmpty_whenIoExceptionThrown() throws Exception {
    doThrow(new IOException("connection refused"))
        .when(httpClient)
        .send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isEmpty());
  }

  @Test
  void findAccountEndpoint_setsInterruptedFlag_whenInterrupted() throws Exception {
    doThrow(new InterruptedException()).when(httpClient).send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isEmpty());
    assertTrue(Thread.interrupted()); // verify and clear the interrupted flag
  }

  @Test
  void findAccountEndpoint_stopsAtFirstMatch_withMultipleProviders() throws Exception {
    when(httpResponse.statusCode()).thenReturn(204);
    doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.PU,
            List.of(InformationService.EpaProvider.IBM, InformationService.EpaProvider.BITMARCK));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isPresent());
    assertEquals(URI.create("https://epa-as-1.prod.epa4all.de"), result.get());
    verify(httpClient, times(1)).send(any(HttpRequest.class), any());
  }

  @Test
  void findAccountEndpoint_checksSecondProvider_whenFirstReturnsNoAccount() throws Exception {
    var firstResponse = mock(HttpResponse.class);
    var secondResponse = mock(HttpResponse.class);
    when(firstResponse.statusCode()).thenReturn(404);
    when(secondResponse.statusCode()).thenReturn(204);
    doReturn(firstResponse).doReturn(secondResponse).when(httpClient).send(any(), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.PU,
            List.of(InformationService.EpaProvider.IBM, InformationService.EpaProvider.BITMARCK));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isPresent());
    assertEquals(URI.create("https://epa-as-2.prod.epa4all.de"), result.get());
  }

  @Test
  void findAccountEndpoint_returnsEmpty_whenNoProviders() {
    var service = new InformationService(httpClient, InformationService.Environment.DEV, List.of());

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isEmpty());
    verifyNoInteractions(httpClient);
  }

  @Test
  void environment_ref_hasCorrectIdentifier() {
    assertEquals("ref", InformationService.Environment.REF.identifier());
  }

  @Test
  void environment_dev_hasCorrectIdentifier() {
    assertEquals("dev", InformationService.Environment.DEV.identifier());
  }

  @Test
  void environment_pu_hasCorrectIdentifier() {
    assertEquals("prod", InformationService.Environment.PU.identifier());
  }

  @Test
  void environment_test_hasCorrectIdentifier() {
    assertEquals("test", InformationService.Environment.TEST.identifier());
  }

  @Test
  void environment_ref_derivesCorrectEndpointUrl() throws Exception {
    when(httpResponse.statusCode()).thenReturn(204);
    doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.REF,
            List.of(InformationService.EpaProvider.IBM));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isPresent());
    assertEquals(URI.create("https://epa-as-1.ref.epa4all.de"), result.get());
  }
}
