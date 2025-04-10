package com.oviva.telematik.epa4all.restservice;

import com.oviva.epa.client.KonnektorService;
import com.oviva.telematik.epa4all.client.Environment;
import com.oviva.telematik.epa4all.client.Epa4AllClient;
import com.oviva.telematik.epa4all.client.Epa4AllClientFactoryBuilder;
import de.gematik.epa.conversion.internal.enumerated.*;
import de.gematik.epa.ihe.model.Author;
import de.gematik.epa.ihe.model.document.Document;
import de.gematik.epa.ihe.model.document.DocumentMetadata;
import de.gematik.epa.ihe.model.simple.AuthorInstitution;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Epa4allClientService {

  private static final Logger log = LoggerFactory.getLogger(Epa4allClientService.class);
  private final KonnektorServiceFactory konnektorServiceProvider;
  private final InetSocketAddress tiProxyAddress;
  private final Environment environment;

  public Epa4allClientService(
      KonnektorServiceFactory konnektorServiceProvider,
      InetSocketAddress tiProxyAddress,
      Environment environment) {
    this.konnektorServiceProvider = konnektorServiceProvider;
    this.tiProxyAddress = tiProxyAddress;
    this.environment = environment;
  }

  public Epa4allClientService.WriteDocumentResponse writeDocument(
      @Nullable String insurantId, @Nullable String contentType, @Nullable byte[] contents) {

    requireNonNull(insurantId, "insurantId");
    requireNonNull(contentType, "contentType");
    requireNonNull(contents, "contents");

    return withClient(
        client -> {
          var authorInstitution = client.authorInstitution();
          var documentId = UUID.randomUUID();
          var document =
              buildDocumentPayload(
                  documentId, insurantId, authorInstitution, contentType, contents);
          client.writeDocument(insurantId, document);
          return new WriteDocumentResponse(documentId);
        });
  }

  private void requireNonNull(Object obj, String name) {
    if (obj == null) {
      throw new BadRequestException(name + " cannot be null");
    }
  }

  public Epa4allClientService.WriteDocumentResponse replaceDocument(
      @NonNull String insurantId,
      @NonNull String contentType,
      @NonNull byte[] contents,
      @NonNull UUID documentToReplaceId) {

    requireNonNull(insurantId, "insurantId");
    requireNonNull(contentType, "contentType");
    requireNonNull(contents, "contents");
    requireNonNull(documentToReplaceId, "documentId");

    return withClient(
        client -> {
          var authorInstitution = client.authorInstitution();
          var document =
              buildDocumentPayload(
                  documentToReplaceId, insurantId, authorInstitution, contentType, contents);
          client.replaceDocument(insurantId, document, documentToReplaceId);
          return new WriteDocumentResponse(documentToReplaceId);
        });
  }

  public boolean isHealthy() {
    try {
      withClient(Epa4AllClient::authorInstitution);
    } catch (Exception e) {
      log.atTrace().setCause(e).log("health check failed");
      return false;
    }
    return true;
  }

  private <T> T withClient(Function<Epa4AllClient, T> inClient) {

    var konnektorService = konnektorServiceProvider.get();
    try (var cf =
        Epa4AllClientFactoryBuilder.newBuilder()
            .konnektorProxyAddress(tiProxyAddress)
            .konnektorService(konnektorService)
            .environment(environment)
            .build()) {

      var client = cf.newClient();

      return inClient.apply(client);
    }
  }

  public static DocumentMetadata buildDocumentMetadata(
      UUID id,
      String insurantId,
      AuthorInstitution authorInstitution,
      String mimeType,
      byte[] contents) {

    // IMPORTANT: Without the urn prefix we can't replace it later
    var documentUuid = "urn:uuid:" + id;

    // some implementations use local time or summertime, 3h is safe
    var createdAt = LocalDateTime.now().minusHours(3);

    // TODO
    return new DocumentMetadata(
        List.of(
            // https://gemspec.gematik.de/docs/gemSpec/gemSpec_DM_ePA_EU-Pilot/gemSpec_DM_ePA_EU-Pilot_V1.53.1/#2.1.4.3.1
            new Author(
                authorInstitution.identifier(),
                authorInstitution.name(),
                authorInstitution.name(),
                "",
                "",
                "",
                // professionOID for DiGA:
                // https://gemspec.gematik.de/docs/gemSpec/gemSpec_OID/gemSpec_OID_V3.19.0/#3.5.1.3
                "1.2.276.0.76.4.282", // OID
                // Der identifier in AuthorInstitution muss eine gültige TelematikId sein, so
                // wie sie z. B. auf der SMC-B-Karte enthalten ist
                List.of(authorInstitution),
                List.of("12^^^&amp;1.3.6.1.4.1.19376.3.276.1.5.13&amp;ISO"),
                List.of("25^^^&1.3.6.1.4.1.19376.3.276.1.5.11&ISO"),
                //
                //                List.of("^^Internet^telematik-infrastructure@oviva.com")) // TODO
                List.of())),
        "AVAILABLE",
        null,
        ClassCode.DURCHFUEHRUNGSPROTOKOLL.getValue(),
        "",
        createdAt,
        documentUuid,
        List.of(EventCode.VIRTUAL_ENCOUNTER.getValue(), EventCode.PATIENTEN_MITGEBRACHT.getValue()),
        FormatCode.DIGA.getValue(),
        "",
        HealthcareFacilityCode.PATIENT_AUSSERHALB_BETREUUNG.getValue(),
        "de-DE",
        "",
        mimeType,
        PracticeSettingCode.PATIENT_AUSSERHALB_BETREUUNG.getValue(),
        List.of(),
        null,
        null,
        contents.length,
        "Export %s".formatted(id),
        TypeCode.PATIENTENEIGENE_DOKUMENTE.getValue(),
        documentUuid,
        "%s Export %s".formatted(authorInstitution.name(), id),
        "",
        "",
        insurantId);
  }

  public static Document buildDocumentPayload(
      UUID id, String kvnr, AuthorInstitution authorInstitution, String mimeType, byte[] contents) {

    var metadata = buildDocumentMetadata(id, kvnr, authorInstitution, mimeType, contents);
    return new Document(contents, metadata, null);
  }

  public interface KonnektorServiceFactory {
    KonnektorService get();
  }

  public record WriteDocumentResponse(UUID documentId) {}
}
