package com.oviva.telematik.epa4all.client.internal;

import com.oviva.epa.client.model.SmcbCard;
import com.oviva.telematik.epa4all.client.ClientException;
import com.oviva.telematik.epa4all.client.DuplicateDocumentClientException;
import com.oviva.telematik.epa4all.client.Epa4AllClient;
import com.oviva.telematik.epaapi.DuplicateDocumentException;
import com.oviva.telematik.epaapi.PhrService;
import com.oviva.telematik.epaapi.SoapClientFactory;
import com.oviva.telematik.epaapi.WriteDocumentException;
import com.oviva.telematik.vau.epa4all.client.authz.AuthorizationService;
import com.oviva.telematik.vau.epa4all.client.info.InformationService;
import de.gematik.epa.ihe.model.document.Document;
import de.gematik.epa.ihe.model.simple.AuthorInstitution;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.UUID;

public class Epa4AllClientImpl implements Epa4AllClient {

  private final InformationService informationService;
  private final AuthorizationService authorizationService;
  private final SmcbCard card;
  private final SoapClientFactory soapClientFactory;

  public Epa4AllClientImpl(
      InformationService informationService,
      AuthorizationService authorizationService,
      SmcbCard card,
      SoapClientFactory soapClientFactory) {

    this.informationService = informationService;
    this.authorizationService = authorizationService;
    this.card = card;
    this.soapClientFactory = soapClientFactory;

    Logs.log(
        "create_client",
        new Logs.Attr("telematik_id", card.telematikId()),
        new Logs.Attr("telematik_name", card.holderName()));
  }

  @NonNull
  @Override
  public AuthorInstitution authorInstitution() {
    Logs.log("get_author_institution");
    return new AuthorInstitution(card.holderName(), card.telematikId());
  }

  @Override
  public void writeDocument(@NonNull String insurantId, @NonNull Document document) {

    Logs.log("write_document");
    try {
      var phrService = openPhrServiceForInsurant(insurantId);
      phrService.writeDocument(insurantId, document);
    } catch (DuplicateDocumentException e) {
      throw new DuplicateDocumentClientException("duplicate document", e);
    } catch (WriteDocumentException e) {
      throw new ClientException("failed to write document", e);
    }
  }

  @Override
  public void replaceDocument(
      @NonNull String insurantId, @NonNull Document document, @NonNull UUID documentToReplaceId) {

    Logs.log("replace_document");
    try {
      var phrService = openPhrServiceForInsurant(insurantId);
      phrService.replaceDocument(insurantId, document, documentToReplaceId);
    } catch (DuplicateDocumentException e) {
      throw new DuplicateDocumentClientException("duplicate document", e);
    } catch (WriteDocumentException e) {
      throw new ClientException("failed to write document", e);
    }
  }

  private PhrService openPhrServiceForInsurant(String insurantId) {

    var endpoint =
        informationService
            .findAccountEndpoint(insurantId)
            .orElseThrow(
                () -> new ClientException("endpoint for KVNR %s not found".formatted(insurantId)));

    authorizationService.authorizeVauWithSmcB(endpoint, insurantId);

    var phrEndpoint =
        UriMangle.downgradeHttpsUri(endpoint)
            .resolve("/epa/xds-document/api/I_Document_Management");

    var phrPort = soapClientFactory.getIDocumentManagementPort(phrEndpoint);
    return new PhrService(phrPort);
  }
}
