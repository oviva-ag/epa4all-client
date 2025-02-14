package com.oviva.telematik.epa4all.client.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.epa4all.client.ClientException;
import com.oviva.telematik.epaapi.SoapClientFactory;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationService;
import com.oviva.telematik.vau.epa4all.client.info.InformationService;
import de.gematik.epa.ihe.model.Author;
import de.gematik.epa.ihe.model.document.Document;
import de.gematik.epa.ihe.model.document.DocumentMetadata;
import de.gematik.epa.ihe.model.simple.AuthorInstitution;
import de.gematik.epa.ihe.model.simple.ByteArray;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    var requestId = "398dkehn9";
    var regRes = mockRegistryResponseType(requestId);

    var documentManagementPort = mockDocumentManagementPort(regRes);

    var document = mockDocumentWithAuthor(TELEMATIK_ID, HOLDER_NAME);

    try (var m = mockStatic(ClientProxy.class)) {
      var endpoint = mock(Client.class);
      when(endpoint.getRequestContext()).thenReturn(new java.util.HashMap<>());
      m.when(() -> ClientProxy.getClient(documentManagementPort)).thenReturn(endpoint);

      // When
      var res = client.writeDocument(INSURANT_ID, document);

      // Then
      assertEquals(requestId, res.requestId());
    }
  }

  private RegistryResponseType mockRegistryResponseType(String requestId) {
    var regRes = mock(RegistryResponseType.class);
    when(regRes.getStatus())
        .thenReturn("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success");
    when(regRes.getRequestId()).thenReturn(requestId);
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
            null),
        null);
  }
}
