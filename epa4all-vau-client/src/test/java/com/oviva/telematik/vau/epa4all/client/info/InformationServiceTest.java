package com.oviva.telematik.vau.epa4all.client.info;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InformationServiceTest {

  private static final String INSURANT_ID = "X123456789";
  private static final String NO_HEALTH_RECORD_BODY = "{\"errorCode\":\"noHealthRecord\"}";
  private static final String MALFORMED_REQUEST_BODY =
      "{\"errorCode\":\"malformedRequest\",\"errorDetail\":\"invalid x-insurantid '%s'\"}"
          .formatted(INSURANT_ID);

  @Mock HttpClient httpClient;

  @Test
  void findAccountEndpoint_returnsEndpoint_whenAccountFound() throws Exception {
    doReturn(response(204)).when(httpClient).send(any(HttpRequest.class), any());

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
    doReturn(response(404)).when(httpClient).send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isEmpty());
  }

  @Test
  void findAccountEndpoint_throwsInformationServiceException_whenIoExceptionThrown()
      throws Exception {
    doThrow(new IOException("connection refused"))
        .when(httpClient)
        .send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var e =
        assertThrows(
            InformationServiceException.class, () -> service.findAccountEndpoint(INSURANT_ID));
    assertTrue(e.getMessage().contains("unreachable (connection refused)"));
  }

  @Test
  void findAccountEndpoint_addsIoExceptionsAsSuppressed_whenProvidersUnreachable()
      throws Exception {
    var first = new IOException("connection refused");
    var second = new IOException("connection reset");
    doThrow(first, second).when(httpClient).send(any(HttpRequest.class), any());

    var e =
        assertThrows(
            InformationServiceException.class,
            () -> twoProviderService().findAccountEndpoint(INSURANT_ID));

    assertArrayEquals(new Throwable[] {first, second}, e.getSuppressed());
  }

  @Test
  void findAccountEndpoint_addsIoExceptionAsSuppressed_whenOtherProviderHasStatusMismatch()
      throws Exception {
    var unreachable = new IOException("connection refused");
    doThrow(unreachable)
        .doReturn(response(409, "{\"errorCode\":\"statusMismatch\"}"))
        .when(httpClient)
        .send(any(HttpRequest.class), any());

    var e =
        assertThrows(
            HealthRecordUnavailableException.class,
            () -> twoProviderService().findAccountEndpoint(INSURANT_ID));

    assertArrayEquals(new Throwable[] {unreachable}, e.getSuppressed());
  }

  @Test
  void findAccountEndpoint_setsInterruptedFlagAndThrows_whenInterrupted() throws Exception {
    doThrow(new InterruptedException()).when(httpClient).send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    assertThrows(InformationServiceException.class, () -> service.findAccountEndpoint(INSURANT_ID));
    assertTrue(Thread.interrupted()); // verify and clear the interrupted flag
  }

  @Test
  void findAccountEndpoint_stopsAtFirstMatch_withMultipleProviders() throws Exception {
    doReturn(response(204)).when(httpClient).send(any(HttpRequest.class), any());

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
    doReturn(response(404)).doReturn(response(204)).when(httpClient).send(any(), any());

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
    doReturn(response(204)).when(httpClient).send(any(HttpRequest.class), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.REF,
            List.of(InformationService.EpaProvider.IBM));

    var result = service.findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isPresent());
    assertEquals(URI.create("https://epa-as-1.ref.epa4all.de"), result.get());
  }

  @Test
  void findAccountEndpoint_returnsEmpty_whenAllProvidersReturnNoHealthRecord() throws Exception {
    doReturn(response(404, NO_HEALTH_RECORD_BODY))
        .doReturn(response(404, NO_HEALTH_RECORD_BODY))
        .when(httpClient)
        .send(any(), any());

    var result = twoProviderService().findAccountEndpoint(INSURANT_ID);

    assertTrue(result.isEmpty());
    verify(httpClient, times(2)).send(any(), any());
  }

  @Test
  void findAccountEndpoint_throwsHealthRecordUnavailable_whenStatusMismatch() throws Exception {
    doReturn(response(409, "{\"errorCode\":\"statusMismatch\"}"))
        .doReturn(response(404, NO_HEALTH_RECORD_BODY))
        .when(httpClient)
        .send(any(), any());

    var e =
        assertThrows(
            HealthRecordUnavailableException.class,
            () -> twoProviderService().findAccountEndpoint(INSURANT_ID));
    assertTrue(e.getMessage().contains("https://epa-as-1.prod.epa4all.de: 409 statusMismatch"));
  }

  @Test
  void findAccountEndpoint_prefersStatusMismatch_overErrorOfOtherProvider() throws Exception {
    doReturn(response(500, "{\"errorCode\":\"internalError\"}"))
        .doReturn(response(409, "{\"errorCode\":\"statusMismatch\"}"))
        .when(httpClient)
        .send(any(), any());

    assertThrows(
        HealthRecordUnavailableException.class,
        () -> twoProviderService().findAccountEndpoint(INSURANT_ID));
  }

  @Test
  void findAccountEndpoint_throwsInformationServiceException_whenInternalError() throws Exception {
    doReturn(response(500, "{\"errorCode\":\"internalError\"}"))
        .when(httpClient)
        .send(any(), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var e =
        assertThrows(
            InformationServiceException.class, () -> service.findAccountEndpoint(INSURANT_ID));
    assertTrue(e.getMessage().contains("500 internalError"));
  }

  @Test
  void
      findAccountEndpoint_throwsInformationServiceException_whenOneProviderFailsAndOtherHasNoRecord()
          throws Exception {
    // must not be reported as "no health record": the failing provider may hold the record
    doReturn(response(404, NO_HEALTH_RECORD_BODY))
        .doReturn(response(500, "{\"errorCode\":\"internalError\"}"))
        .when(httpClient)
        .send(any(), any());

    var e =
        assertThrows(
            InformationServiceException.class,
            () -> twoProviderService().findAccountEndpoint(INSURANT_ID));
    assertTrue(e.getMessage().contains("https://epa-as-1.prod.epa4all.de: 404"));
    assertTrue(e.getMessage().contains("https://epa-as-2.prod.epa4all.de: 500 internalError"));
  }

  @Test
  void findAccountEndpoint_throwsInformationServiceException_whenUnexpectedStatus()
      throws Exception {
    doReturn(response(502, "<html>Bad Gateway</html>")).when(httpClient).send(any(), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var e =
        assertThrows(
            InformationServiceException.class, () -> service.findAccountEndpoint(INSURANT_ID));
    assertTrue(e.getMessage().contains("502"));
  }

  @Test
  void findAccountEndpoint_throwsMalformedRequestException_whenMalformedRequest() throws Exception {
    doReturn(response(400, MALFORMED_REQUEST_BODY)).when(httpClient).send(any(), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var e =
        assertThrows(
            InformationServiceMalformedRequestException.class,
            () -> service.findAccountEndpoint(INSURANT_ID));
    assertTrue(e.getMessage().contains("400 malformedRequest"));
    assertFalse(e.getMessage().contains(INSURANT_ID));
  }

  @Test
  void findAccountEndpoint_throwsMalformedRequestException_whenOtherProviderHasNoRecord()
      throws Exception {
    doReturn(response(404, NO_HEALTH_RECORD_BODY))
        .doReturn(response(400, MALFORMED_REQUEST_BODY))
        .when(httpClient)
        .send(any(), any());

    assertThrows(
        InformationServiceMalformedRequestException.class,
        () -> twoProviderService().findAccountEndpoint(INSURANT_ID));
  }

  @Test
  void findAccountEndpoint_prefersMalformedRequest_overErrorOfOtherProvider() throws Exception {
    doReturn(response(500, "{\"errorCode\":\"internalError\"}"))
        .doReturn(response(400, MALFORMED_REQUEST_BODY))
        .when(httpClient)
        .send(any(), any());

    assertThrows(
        InformationServiceMalformedRequestException.class,
        () -> twoProviderService().findAccountEndpoint(INSURANT_ID));
  }

  @Test
  void findAccountEndpoint_prefersStatusMismatch_overMalformedRequestOfOtherProvider()
      throws Exception {
    doReturn(response(400, MALFORMED_REQUEST_BODY))
        .doReturn(response(409, "{\"errorCode\":\"statusMismatch\"}"))
        .when(httpClient)
        .send(any(), any());

    assertThrows(
        HealthRecordUnavailableException.class,
        () -> twoProviderService().findAccountEndpoint(INSURANT_ID));
  }

  @Test
  void findAccountEndpoint_sendsDocumentedHeaders() throws Exception {
    doReturn(response(204)).when(httpClient).send(any(), any());

    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    service.findAccountEndpoint(INSURANT_ID);

    verify(httpClient)
        .send(
            argThat(
                (HttpRequest r) ->
                    r.uri()
                            .equals(
                                URI.create(
                                    "https://epa-as-1.dev.epa4all.de/information/api/v1/ehr"))
                        && r.headers().firstValue("x-insurantid").orElse("").equals(INSURANT_ID)
                        && r.headers().firstValue("x-useragent").isPresent()),
            any());
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 409, 500})
  void findAccountEndpoint_doesNotLogKvnr(int status) throws Exception {
    var body =
        "{\"errorCode\":\"someError\",\"errorDetail\":\"insurant %s\"}".formatted(INSURANT_ID);
    doReturn(response(status, body)).when(httpClient).send(any(), any());
    var service =
        new InformationService(
            httpClient,
            InformationService.Environment.DEV,
            List.of(InformationService.EpaProvider.IBM));

    var logs = captureStdErr(() -> service.findAccountEndpoint(INSURANT_ID));

    assertTrue(logs.contains("someError"), logs);
    assertFalse(logs.contains(INSURANT_ID), logs);
  }

  private static String captureStdErr(Runnable action) {
    var original = System.err;
    var captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      action.run();
    } catch (RuntimeException ignored) {
    } finally {
      System.setErr(original);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  private InformationService twoProviderService() {
    return new InformationService(
        httpClient,
        InformationService.Environment.PU,
        List.of(InformationService.EpaProvider.IBM, InformationService.EpaProvider.BITMARCK));
  }

  private static HttpResponse<String> response(int status) {
    return response(status, "");
  }

  private static HttpResponse<String> response(int status, String body) {
    return new FakeResponse(status, body);
  }

  private record FakeResponse(int statusCode, String body) implements HttpResponse<String> {

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(
          Map.of(
              "content-type", List.of("application/json"),
              "x-request-id", List.of("2b1e7c1a-0000-4000-8000-000000000000"),
              "set-cookie", List.of("session=secret")),
          (k, v) -> true);
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return URI.create("https://epa-as-1.dev.epa4all.de/information/api/v1/ehr");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }
}
