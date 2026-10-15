package com.oviva.telematik.vau.epa4all.client.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oviva.telematik.vau.epa4all.client.authz.internal.JsonCodec;
import com.oviva.telematik.vau.epa4all.client.info.InformationServiceException.Reason;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InformationService {

  private static final Logger log = LoggerFactory.getLogger(InformationService.class);

  // full request/response dumps, enabled with EPA4ALL_HTTP_DEBUG_LOG=true
  private static final Logger httpDebugLog = LoggerFactory.getLogger("http-debug");

  private static final String RECORD_STATUS_PATH = "/information/api/v1/ehr";
  private static final String USER_AGENT = "Oviva/0.0.1";

  private static final Pattern KVNR_FORMAT = Pattern.compile("[A-Z][0-9]{9}");
  private final HttpClient outerHttpClient;

  private final List<URI> epaEndpoints;

  public InformationService(
      java.net.http.HttpClient outerHttpClient,
      Environment environment,
      List<EpaProvider> providers) {

    this.outerHttpClient = outerHttpClient;
    epaEndpoints = providers.stream().map(p -> deriveEndpoint(environment, p)).toList();
  }

  public Optional<URI> findAccountEndpoint(String insurantId) {
    var outcomes = new ArrayList<String>();
    var causes = new ArrayList<Exception>();
    var unavailable = false;
    var malformed = false;
    var failed = false;

    for (URI epaEndpoint : epaEndpoints) {
      var status = getRecordStatus(insurantId, epaEndpoint);
      outcomes.add("%s: %s".formatted(epaEndpoint, status.description()));
      if (status.cause() != null) {
        causes.add(status.cause());
      }
      switch (status.result()) {
        case ACTIVATED -> {
          return Optional.of(epaEndpoint);
        }
        case NO_HEALTH_RECORD -> {
          // keep looking, the record may be held by another Aktensystem
        }
        case STATUS_MISMATCH -> unavailable = true;
        case MALFORMED_REQUEST -> malformed = true;
        case ERROR -> failed = true;
      }
    }

    if (unavailable) {
      throw withSuppressed(
          new InformationServiceException(
              Reason.RECORD_UNAVAILABLE,
              "health record is not in state ACTIVATED (SUSPENDED or INACCESSIBLE): %s"
                  .formatted(outcomes)),
          causes);
    }
    if (malformed) {
      throw withSuppressed(
          new InformationServiceException(
              Reason.MALFORMED_REQUEST,
              "Information Service rejected the request as malformed: %s".formatted(outcomes)),
          causes);
    }
    if (failed) {
      throw withSuppressed(
          new InformationServiceException(
              Reason.ERROR, "could not determine health record status: %s".formatted(outcomes)),
          causes);
    }
    return Optional.empty();
  }

  private static InformationServiceException withSuppressed(
      InformationServiceException exception, List<Exception> causes) {
    causes.forEach(exception::addSuppressed);
    return exception;
  }

  private RecordStatus getRecordStatus(String insurantId, URI endpoint) {
    var httpRequest =
        HttpRequest.newBuilder(endpoint.resolve(RECORD_STATUS_PATH))
            .headers("x-useragent", USER_AGENT, "x-insurantid", insurantId)
            .GET()
            .build();

    HttpResponse<String> httpResponse;
    try {
      httpResponse = outerHttpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      log.atInfo()
          .addKeyValue("endpoint", endpoint)
          .setCause(e)
          .log("failed to reach epa record at '{}': {}", endpoint, e.getMessage());
      return new RecordStatus(Result.ERROR, "unreachable (%s)".formatted(e.getMessage()), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InformationServiceException(
          Reason.ERROR, "interrupted while querying epa record at '%s'".formatted(endpoint), e);
    }

    // status code mapping according to I_Information_Service getRecordStatus
    var status = httpResponse.statusCode();
    if (status == 204) {
      return new RecordStatus(Result.ACTIVATED, "204", null);
    }
    if (status == 404) {
      return new RecordStatus(Result.NO_HEALTH_RECORD, "404", null);
    }

    var error = parseError(httpResponse.body());
    var description = "%d %s".formatted(status, errorCodeOf(error)).strip();
    if (status == 400) {
      log.atWarn()
          .addKeyValue("endpoint", httpResponse.uri())
          .addKeyValue("status", status)
          .addKeyValue("errorCode", errorCodeOf(error))
          .addKeyValue("errorDetail", error != null ? mask(error.errorDetail(), insurantId) : null)
          .log(
              "Information Service rejected getRecordStatus as malformed at '{}': status={},"
                  + " errorCode={}",
              httpResponse.uri(),
              status,
              errorCodeOf(error));
      debugLogMalformedRequest(httpRequest, httpResponse, insurantId);
      return new RecordStatus(Result.MALFORMED_REQUEST, description, null);
    }
    if (status == 409) {
      logErrorResponse(httpResponse, error, insurantId, "health record is not in state ACTIVATED");
      return new RecordStatus(Result.STATUS_MISMATCH, description, null);
    }
    logErrorResponse(httpResponse, error, insurantId, "Information Service returned an error");
    return new RecordStatus(Result.ERROR, description, null);
  }

  private void logErrorResponse(
      HttpResponse<String> res, ErrorType error, String insurantId, String message) {
    log.atInfo()
        .addKeyValue("endpoint", res.uri())
        .addKeyValue("status", res.statusCode())
        .addKeyValue("errorCode", errorCodeOf(error))
        .addKeyValue("errorDetail", error != null ? mask(error.errorDetail(), insurantId) : null)
        .log(
            "{} at '{}': status={}, errorCode={}",
            message,
            res.uri(),
            res.statusCode(),
            errorCodeOf(error));
  }

  /**
   * A '400 malformedRequest' means our request did not match the Information Service schema, so
   * retrying won't help. Dump the request and response to debug it, without the KVNR itself.
   */
  private void debugLogMalformedRequest(
      HttpRequest req, HttpResponse<String> res, String insurantId) {
    if (!httpDebugLog.isDebugEnabled()) {
      return;
    }
    httpDebugLog
        .atDebug()
        .addKeyValue("request.method", req.method())
        .addKeyValue("request.uri", req.uri())
        .addKeyValue("request.header.x-useragent", USER_AGENT)
        .addKeyValue("request.insurantId.length", insurantId == null ? null : insurantId.length())
        .addKeyValue(
            "request.insurantId.matchesKvnrFormat",
            insurantId != null && KVNR_FORMAT.matcher(insurantId).matches())
        .addKeyValue("response.status", res.statusCode())
        .addKeyValue("response.version", res.version())
        .addKeyValue("response.body", mask(res.body(), insurantId))
        .log("< Information Service response: {} {}", req.method(), req.uri());
  }

  private static String mask(String value, String insurantId) {
    if (value == null || insurantId == null || insurantId.isEmpty()) {
      return value;
    }
    return value.replace(insurantId, "<insurantId>");
  }

  private static String errorCodeOf(ErrorType error) {
    return error != null && error.errorCode() != null ? error.errorCode() : "";
  }

  private static ErrorType parseError(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      return JsonCodec.readString(body, ErrorType.class);
    } catch (IllegalArgumentException e) {
      log.atInfo().setCause(e).log("Failed to parse error body as ErrorType");
      return null;
    }
  }

  // based on
  // https://github.com/gematik/ePA-Basic/blob/3186ad27a1a865bc3b9ccbe6a4d4c697883903e6/src/openapi/I_Information_Service.yaml#L112-L116
  private enum Result {
    ACTIVATED,
    NO_HEALTH_RECORD,
    STATUS_MISMATCH,
    MALFORMED_REQUEST,
    ERROR
  }

  private record RecordStatus(Result result, String description, Exception cause) {}

  private record ErrorType(
      @JsonProperty("errorCode") String errorCode,
      @JsonProperty("errorDetail") String errorDetail) {}

  private static URI deriveEndpoint(Environment environment, EpaProvider provider) {
    return URI.create(
        "https://epa-as-%d.%s.epa4all.de".formatted(provider.id(), environment.identifier()));
  }

  public record EpaProvider(int id) {
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_Aktensystem_ePAfueralle/gemSpec_Aktensystem_ePAfueralle_V1.3.0/#A_24592-02
    public static EpaProvider IBM = new EpaProvider(1);
    public static EpaProvider BITMARCK = new EpaProvider(2);
  }

  public enum Environment {
    REF("ref"),
    DEV("dev"), // also known as RU2
    TEST("test"),
    PU("prod");
    private final String identifier;

    Environment(String identifier) {
      this.identifier = identifier;
    }

    public String identifier() {
      return identifier;
    }
  }
}
