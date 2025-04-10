package com.oviva.telematik.epa4all.client;

import de.gematik.epa.ihe.model.document.Document;
import de.gematik.epa.ihe.model.simple.AuthorInstitution;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.UUID;

public interface Epa4AllClient {

  /**
   * @return the author institution related to this client, usually read from the SMC-B card
   */
  @NonNull
  AuthorInstitution authorInstitution();

  /**
   * write a document to an insurants electronic health record
   *
   * @param insurantId the KVNR of the insurant (patient)
   * @param document the document to write into the electronic health record
   */
  void writeDocument(@NonNull String insurantId, @NonNull Document document);

  /**
   * replace a document in an insurants electronic health record
   *
   * @param insurantId the KVNR of the insurant (patient)
   * @param document the document to write into the electronic health record
   * @param documentToReplaceId the ID of the document to replace
   */
  void replaceDocument(
      @NonNull String insurantId, @NonNull Document document, @NonNull UUID documentToReplaceId);
}
