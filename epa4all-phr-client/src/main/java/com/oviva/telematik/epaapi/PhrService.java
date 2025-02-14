package com.oviva.telematik.epaapi;

import com.oviva.telematik.epaapi.internal.InsurantAdapter;
import de.gematik.epa.LibIheXdsMain;
import de.gematik.epa.ihe.model.document.Document;
import de.gematik.epa.ihe.model.document.DocumentMetadata;
import de.gematik.epa.ihe.model.request.DocumentSubmissionRequest;
import de.gematik.epa.ihe.model.simple.SubmissionSetMetadata;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import java.time.LocalDateTime;
import java.util.*;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.message.Message;
import telematik.ws.phr.wsdl.IDocumentManagementPortType;

public class PhrService {

  private static final String REGISTRY_STATUS_SUCCESS =
      "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success";

  private final IDocumentManagementPortType documentManagementPort;

  public PhrService(IDocumentManagementPortType documentManagementPort) {
    this.documentManagementPort = documentManagementPort;
  }

  public String writeDocument(String insurantId, Document document) {

    var metadata = getSubmissionSetMetadata(document.documentMetadata());
    var docSubmissionRequest =
        new DocumentSubmissionRequest(new InsurantAdapter(insurantId), List.of(document), metadata);

    var req = LibIheXdsMain.convertDocumentSubmissionRequest(docSubmissionRequest);

    var res = callWriteDocument(insurantId, req);
    validateResponse(res);

    return res.getRequestId();
  }

  private RegistryResponseType callWriteDocument(
      String insurantId, ProvideAndRegisterDocumentSetRequestType req) {
    try {
      setInsurantIdForCurrentRequestContext(insurantId);
      return documentManagementPort.documentRepositoryProvideAndRegisterDocumentSetB(req);
    } finally {
      resetHeadersForCurrentRequestContext();
    }
  }

  private void setInsurantIdForCurrentRequestContext(String insurantId) {
    // NOTE: This completely works via side-effects. The assumption is that port-proxies are thread
    // safe.
    var headers = new HashMap<>(Map.of("x-insurantid", List.of(insurantId)));
    ClientProxy.getClient(documentManagementPort)
        .getRequestContext()
        .put(Message.PROTOCOL_HEADERS, headers);
  }

  private void resetHeadersForCurrentRequestContext() {
    // NOTE: Let's do our best to remove the headers we set earlier as the port will likely be
    // re-used.
    ClientProxy.getClient(documentManagementPort)
        .getRequestContext()
        .put(Message.PROTOCOL_HEADERS, new HashMap<>());
  }

  public String replaceDocument(String insurantId, Document document, UUID documentToReplaceId) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  private SubmissionSetMetadata getSubmissionSetMetadata(DocumentMetadata metadata) {

    var author =
        metadata.author().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("no author"));

    return new SubmissionSetMetadata(List.of(author), null, LocalDateTime.now(), null, null, null);
  }

  private void validateResponse(RegistryResponseType res) {

    if (REGISTRY_STATUS_SUCCESS.equals(res.getStatus())) {
      return;
    }
    var errors =
        Optional.ofNullable(res.getRegistryErrorList())
            .map(RegistryErrorList::getRegistryError)
            .stream()
            .flatMap(Collection::stream)
            .map(
                e ->
                    new WriteDocumentException.Error(
                        e.getValue(),
                        e.getCodeContext(),
                        e.getErrorCode(),
                        e.getSeverity(),
                        e.getLocation()))
            .toList();
    throw new WriteDocumentException(
        "writing document failed, status='%s'".formatted(res.getStatus()), errors);
  }
}
