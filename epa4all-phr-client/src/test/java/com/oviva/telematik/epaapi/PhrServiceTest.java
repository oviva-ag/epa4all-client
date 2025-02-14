package com.oviva.telematik.epaapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.gematik.epa.LibIheXdsMain;
import de.gematik.epa.ihe.model.Author;
import de.gematik.epa.ihe.model.document.Document;
import de.gematik.epa.ihe.model.document.DocumentMetadata;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import telematik.ws.phr.wsdl.IDocumentManagementPortType;

@ExtendWith(MockitoExtension.class)
class PhrServiceTest {

  @Mock private IDocumentManagementPortType documentManagementPort;
  @Mock private Document document;
  @Mock private DocumentMetadata documentMetadata;
  @Mock private ProvideAndRegisterDocumentSetRequestType convertedRequest;
  @Mock private Client clientMock;

  @InjectMocks private PhrService phrService;

  private final String insurantId = "X123456789";
  private final String requestId = "req-123";

  @BeforeEach
  void setUp() {
    //    phrService = new PhrService(documentManagementPort);

  }

  @Test
  void writeDocument_successful() {
    // Setup document metadata with author
    when(document.documentMetadata()).thenReturn(documentMetadata);
    when(documentMetadata.author()).thenReturn(List.of(mock(Author.class)));

    // Given
    try (var libIheXdsMainMock = mockStatic(LibIheXdsMain.class);
        var clientProxyMock = mockStatic(ClientProxy.class)) {

      // Mock LibIheXdsMain conversion
      libIheXdsMainMock
          .when(() -> LibIheXdsMain.convertDocumentSubmissionRequest(any()))
          .thenReturn(convertedRequest);

      // Mock ClientProxy for header setting
      clientProxyMock
          .when(() -> ClientProxy.getClient(documentManagementPort))
          .thenReturn(clientMock);

      // Mock request context and capture what is put into it
      var requestContext = mock(Map.class);
      when(clientMock.getRequestContext()).thenReturn(requestContext);

      // Setup argument captor for the headers map
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, List<String>>> headersCaptor = ArgumentCaptor.forClass(Map.class);

      // Mock successful response
      var response = new RegistryResponseType();
      response.setStatus("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success");
      response.setRequestId(requestId);

      when(documentManagementPort.documentRepositoryProvideAndRegisterDocumentSetB(
              convertedRequest))
          .thenReturn(response);

      // When
      var result = phrService.writeDocument(insurantId, document);

      // Then
      assertEquals(requestId, result);

      // Verify headers were set correctly when initially set
      verify(clientMock, times(2)).getRequestContext();
      verify(clientMock.getRequestContext(), times(2))
          .put(eq(Message.PROTOCOL_HEADERS), headersCaptor.capture());

      // Get the first captured value (the headers map when setting)
      var capturedHeaders = headersCaptor.getAllValues().get(0);
      assertNotNull(capturedHeaders);
      assertEquals(List.of(insurantId), capturedHeaders.get("x-insurantid"));

      // Verify the second captured value (reset headers)
      var resetHeaders = headersCaptor.getAllValues().get(1);
      assertTrue(resetHeaders.isEmpty(), "Headers should be reset to empty map");
    }
  }

  @Test
  void writeDocument_handlesFailureResponse() {
    // Setup document metadata with author
    when(document.documentMetadata()).thenReturn(documentMetadata);
    when(documentMetadata.author()).thenReturn(List.of(mock(Author.class)));

    // Given
    try (MockedStatic<LibIheXdsMain> libIheXdsMainMock = mockStatic(LibIheXdsMain.class);
        MockedStatic<ClientProxy> clientProxyMock = mockStatic(ClientProxy.class)) {

      // Mock LibIheXdsMain conversion
      libIheXdsMainMock
          .when(() -> LibIheXdsMain.convertDocumentSubmissionRequest(any()))
          .thenReturn(convertedRequest);

      // Mock ClientProxy for header setting
      clientProxyMock
          .when(() -> ClientProxy.getClient(documentManagementPort))
          .thenReturn(clientMock);

      // Mock request context
      var requestContext = new HashMap<String, Object>();
      when(clientMock.getRequestContext()).thenReturn(requestContext);

      // Mock error response
      var response = new RegistryResponseType();
      response.setStatus("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure");

      var errorList = new RegistryErrorList();
      var error = new RegistryError();
      error.setValue("Error occurred");
      error.setErrorCode("E123");
      error.setSeverity("Error");
      error.setCodeContext("Test error");
      error.setLocation("somewhere");
      errorList.getRegistryError().add(error);
      response.setRegistryErrorList(errorList);

      when(documentManagementPort.documentRepositoryProvideAndRegisterDocumentSetB(
              convertedRequest))
          .thenReturn(response);

      // When & Then
      var exception =
          assertThrows(
              WriteDocumentException.class, () -> phrService.writeDocument(insurantId, document));

      assertEquals(1, exception.errors().size());
      var firstError = exception.errors().get(0);
      assertEquals("Error occurred", firstError.value());
      assertEquals("E123", firstError.errorCode());
      assertEquals("Error", firstError.severity());
      assertEquals("Test error", firstError.codeContext());
      assertEquals("somewhere", firstError.location());

      // Verify headers were reset even when exception occurred
      verify(clientMock, times(2)).getRequestContext();
    }
  }

  @Test
  void writeDocument_requiresAuthor() {

    // Given
    when(document.documentMetadata()).thenReturn(documentMetadata);
    when(documentMetadata.author()).thenReturn(List.of());

    // When & Then
    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> phrService.writeDocument(insurantId, document));

    assertEquals("no author", exception.getMessage());
  }

  @Test
  void replaceDocument_notImplemented() {
    // When & Then
    assertThrows(
        UnsupportedOperationException.class,
        () -> phrService.replaceDocument(insurantId, document, UUID.randomUUID()));
  }
}
