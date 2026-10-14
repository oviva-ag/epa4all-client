package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.epa4all.client.ClientException;
import com.oviva.telematik.epa4all.client.EpaInformationServiceMalformedRequestClientException;
import com.oviva.telematik.epa4all.client.EpaInformationServiceUnavailableClientException;
import com.oviva.telematik.epa4all.client.EpaRecordUnavailableClientException;
import com.oviva.telematik.epa4all.client.NotAuthorizedClientException;
import com.oviva.telematik.epaapi.SoapClientFactory;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationService;
import com.oviva.telematik.vau.epa4all.client.info.HealthRecordUnavailableException;
import com.oviva.telematik.vau.epa4all.client.info.InformationService;
import com.oviva.telematik.vau.epa4all.client.info.InformationServiceException;
import com.oviva.telematik.vau.epa4all.client.info.InformationServiceMalformedRequestException;
import de.gematik.epa.ihe.model.Author;
import de.gematik.epa.ihe.model.document.Document;
import de.gematik.epa.ihe.model.document.DocumentMetadata;
import de.gematik.epa.ihe.model.simple.AuthorInstitution;
import de.gematik.epa.ihe.model.simple.ByteArray;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import telematik.ws.phr.wsdl.IDocumentManagementPortType;

@ExtendWith(MockitoExtension.class)
class Epa4AllClientImplTest {

  @Mock private InformationService informationService;
  @Mock private AuthorizationService authorizationService;
  @Mock private SmcbCard card;
  @Mock private SoapClientFactory soapClientFactory;

  @InjectMocks private Epa4AllClientImpl client;

  private static final String INSURANT_ID = "X123456789";
  private static final String HOLDER_NAME = "Test Practice";
  private static final String TELEMATIK_ID = "1-2-3-TelematikId";
  private static final URI HTTPS_ENDPOINT_URI = URI.create("https://epa.example.com");
  private static final URI HTTP_ENDPOINT_URI = URI.create("http://epa.example.com");

  @Test
  void writeDocument_success() {

    // Given

    when(informationService.findAccountEndpoint(INSURANT_ID))
        .thenReturn(Optional.of(HTTPS_ENDPOINT_URI));
    doNothing().when(authorizationService).authorizeVauWithSmcB(HTTPS_ENDPOINT_URI, INSURANT_ID);

    var regRes = mockRegistryResponseType();

    var documentManagementPort = mockDocumentManagementPort(regRes);

    var document = mockDocumentWithAuthor(TELEMATIK_ID, HOLDER_NAME);

    try (var m = mockStatic(ClientProxy.class)) {
      var endpoint = mock(Client.class);
      when(endpoint.getRequestContext()).thenReturn(new java.util.HashMap<>());
      m.when(() -> ClientProxy.getClient(documentManagementPort)).thenReturn(endpoint);

      // When & Then
      assertDoesNotThrow(() -> client.writeDocument(INSURANT_ID, document));
    }
  }

  @Test
  void writeDocument_notEntitled_throwsNotAuthorizedClientException() {
    // Given
    when(informationService.findAccountEndpoint(INSURANT_ID))
        .thenReturn(Optional.of(HTTPS_ENDPOINT_URI));
    var documentManagementPort = mockDocumentManagementPort(notEntitledRegistryResponse());
    var document = mockDocumentWithAuthor(TELEMATIK_ID, HOLDER_NAME);

    try (var m = mockStatic(ClientProxy.class)) {
      mockClientProxy(m, documentManagementPort);

      // When & Then
      assertThrows(
          NotAuthorizedClientException.class, () -> client.writeDocument(INSURANT_ID, document));
    }
  }

  @Test
  void replaceDocument_notEntitled_throwsNotAuthorizedClientException() {
    // Given
    when(informationService.findAccountEndpoint(INSURANT_ID))
        .thenReturn(Optional.of(HTTPS_ENDPOINT_URI));
    var documentManagementPort = mockDocumentManagementPort(notEntitledRegistryResponse());
    var document = mockDocumentWithAuthor(TELEMATIK_ID, HOLDER_NAME);
    var documentToReplaceId = UUID.randomUUID();

    try (var m = mockStatic(ClientProxy.class)) {
      mockClientProxy(m, documentManagementPort);

      // When & Then
      assertThrows(
          NotAuthorizedClientException.class,
          () -> client.replaceDocument(INSURANT_ID, document, documentToReplaceId));
    }
  }

  private void mockClientProxy(
      MockedStatic<ClientProxy> m, IDocumentManagementPortType documentManagementPort) {
    var endpoint = mock(Client.class);
    when(endpoint.getRequestContext()).thenReturn(new java.util.HashMap<>());
    m.when(() -> ClientProxy.getClient(documentManagementPort)).thenReturn(endpoint);
  }

  private RegistryResponseType notEntitledRegistryResponse() {
    var error = new RegistryError();
    error.setErrorCode("NotEntitled");

    var errorList = new RegistryErrorList();
    errorList.getRegistryError().add(error);

    var regRes = new RegistryResponseType();
    regRes.setStatus("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure");
    regRes.setRegistryErrorList(errorList);
    return regRes;
  }

  private RegistryResponseType mockRegistryResponseType() {
    var regRes = mock(RegistryResponseType.class);
    when(regRes.getStatus())
        .thenReturn("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success");
    return regRes;
  }

  private IDocumentManagementPortType mockDocumentManagementPort(RegistryResponseType regRes) {

    var documentManagementPort = mock(IDocumentManagementPortType.class);
    when(soapClientFactory.getIDocumentManagementPort(
            argThat(u -> u.toString().startsWith(HTTP_ENDPOINT_URI.toString()))))
        .thenReturn(documentManagementPort);
    when(documentManagementPort.documentRepositoryProvideAndRegisterDocumentSetB(any()))
        .thenReturn(regRes);
    return documentManagementPort;
  }

  @Test
  void authorInstitution_shouldReturnInstitutionWithCardDetails() {
    // Given
    when(card.holderName()).thenReturn(HOLDER_NAME);
    when(card.telematikId()).thenReturn(TELEMATIK_ID);

    // When
    var result = client.authorInstitution();

    // Then
    assertNotNull(result);
    assertEquals(HOLDER_NAME, result.name());
    assertEquals(TELEMATIK_ID, result.identifier());
  }

  @Test
  void writeDocument_shouldThrowExceptionWhenEndpointNotFound() {
    // Given
    when(informationService.findAccountEndpoint(INSURANT_ID)).thenReturn(Optional.empty());

    // When & Then
    var exception =
        assertThrows(
            ClientException.class, () -> client.writeDocument(INSURANT_ID, mockDocument()));

    // Verify exception message
    assertEquals("endpoint for KVNR X123456789 not found", exception.getMessage());
  }

  @Test
  void replaceDocument_shouldThrowExceptionWhenEndpointNotFound() {
    // Given
    when(informationService.findAccountEndpoint(INSURANT_ID)).thenReturn(Optional.empty());
    var documentToReplaceId = UUID.randomUUID();

    // When & Then
    var exception =
        assertThrows(
            ClientException.class,
            () -> client.replaceDocument(INSURANT_ID, mockDocument(), documentToReplaceId));

    // Verify exception message
    assertEquals("endpoint for KVNR X123456789 not found", exception.getMessage());
  }

  @Test
  void writeDocument_malformedRequest_throwsEpaInformationServiceMalformedRequestClientException() {
    when(informationService.findAccountEndpoint(INSURANT_ID))
        .thenThrow(new InformationServiceMalformedRequestException("400 malformedRequest"));

    var exception =
        assertThrows(
            EpaInformationServiceMalformedRequestClientException.class,
            () -> client.writeDocument(INSURANT_ID, mockDocument()));

    assertEquals(
        "Information Service rejected the request for KVNR X123456789 as malformed",
        exception.getMessage());
  }

  @Test
  void
      replaceDocument_malformedRequest_throwsEpaInformationServiceMalformedRequestClientException() {
    when(informationService.findAccountEndpoint(INSURANT_ID))
        .thenThrow(new InformationServiceMalformedRequestException("400 malformedRequest"));
    var documentToReplaceId = UUID.randomUUID();

    assertThrows(
        EpaInformationServiceMalformedRequestClientException.class,
        () -> client.replaceDocument(INSURANT_ID, mockDocument(), documentToReplaceId));
  }

  @Test
  void writeDocument_recordUnavailable_throwsEpaRecordUnavailableClientException() {
    when(informationService.findAccountEndpoint(INSURANT_ID))
        .thenThrow(new HealthRecordUnavailableException("SUSPENDED"));

    var exception =
        assertThrows(
            EpaRecordUnavailableClientException.class,
            () -> client.writeDocument(INSURANT_ID, mockDocument()));

    assertEquals("ePA record for KVNR X123456789 temporarily unavailable", exception.getMessage());
  }

  @Test
  void
      replaceDocument_informationServiceFailed_throwsEpaInformationServiceUnavailableClientException() {
    when(informationService.findAccountEndpoint(INSURANT_ID))
        .thenThrow(new InformationServiceException("500 internalError"));
    var documentToReplaceId = UUID.randomUUID();

    var exception =
        assertThrows(
            EpaInformationServiceUnavailableClientException.class,
            () -> client.replaceDocument(INSURANT_ID, mockDocument(), documentToReplaceId));

    assertEquals(
        "failed to determine ePA record status for KVNR X123456789", exception.getMessage());
  }

  private Document mockDocument() {
    return new Document((ByteArray) null, null, null);
  }

  private Document mockDocumentWithAuthor(String identifier, String name) {
    var author =
        new Author(
            identifier,
            "Oviva Direkt für Adipositas",
            "Oviva AG",
            "",
            "",
            "",
            // professionOID for DiGA:
            // https://gemspec.gematik.de/docs/gemSpec/gemSpec_OID/gemSpec_OID_V3.19.0/#3.5.1.3
            "1.2.276.0.76.4.282", // OID
            List.of(new AuthorInstitution(name, identifier)),
            List.of("12^^^&amp;1.3.6.1.4.1.19376.3.276.1.5.13&amp;ISO"),
            List.of("25^^^&1.3.6.1.4.1.19376.3.276.1.5.11&ISO"),
            List.of("^^Internet^telematik-infrastructure@oviva.com"));

    return new Document(
        new byte[0],
        new DocumentMetadata(
            List.of(author),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null),
        null);
  }
}
