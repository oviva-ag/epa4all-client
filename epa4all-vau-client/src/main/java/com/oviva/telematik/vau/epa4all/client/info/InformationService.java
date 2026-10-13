package com.oviva.telematik.vau.epa4all.client.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oviva.telematik.vau.epa4all.client.authz.internal.JsonCodec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InformationService {

  private static final Logger log = LoggerFactory.getLogger(InformationService.class);

  private static final String RECORD_STATUS_PATH = "/information/api/v1/ehr";
  private static final String USER_AGENT = "Oviva/0.0.1";

  // unchangeable part of the KVNR: one capital letter followed by nine digits
  private static final Pattern KVNR_FORMAT = Pattern.compile("[A-Z][0-9]{9}");
  private static final int MAX_LOGGED_BODY_LENGTH = 2000;
  private final HttpClient outerHttpClient;

  private final List<URI> epaEndpoints;

  public InformationService(
      java.net.http.HttpClient outerHttpClient,
      Environment environment,
      List<EpaProvider> providers) {

    this.outerHttpClient = outerHttpClient;
    epaEndpoints = providers.stream().map(p -> deriveEndpoint(environment, p)).toList();
  }

  /**
   * Finds the Aktensystem holding the insurant's health record.
   *
   * @return the endpoint of the Aktensystem with the record in state ACTIVATED, or empty if every
   *     Aktensystem answered '404 noHealthRecord'
   * @throws HealthRecordUnavailableException if the record exists but is SUSPENDED or INACCESSIBLE
   * @throws InformationServiceException if the record status could not be determined, e.g. because
   *     an Aktensystem was unreachable or returned an error
   */
  public Optional<URI> findAccountEndpoint(String insurantId) {
    var outcomes = new ArrayList<String>();
    var unavailable = false;
    var failed = false;

    for (URI epaEndpoint : epaEndpoints) {
      var status = getRecordStatus(insurantId, epaEndpoint);
      outcomes.add("%s: %s".formatted(epaEndpoint, status.description()));
      switch (status.result()) {
        case ACTIVATED -> {
          return Optional.of(epaEndpoint);
        }
        case NO_HEALTH_RECORD -> {
          // keep looking, the record may be held by another Aktensystem
        }
        case STATUS_MISMATCH -> unavailable = true;
        case ERROR -> failed = true;
      }
    }

    if (unavailable) {
      throw new HealthRecordUnavailableException(
          "health record is not in state ACTIVATED (SUSPENDED or INACCESSIBLE): %s"
              .formatted(outcomes));
    }
    if (failed) {
      throw new InformationServiceException(
          "could not determine health record status: %s".formatted(outcomes));
    }
    return Optional.empty();
  }

  private RecordStatus getRecordStatus(String insurantId, URI endpoint) {
    var req =
        HttpRequest.newBuilder(endpoint.resolve(RECORD_STATUS_PATH))
            .headers("x-useragent", USER_AGENT, "x-insurantid", insurantId)
            .GET()
            .build();

    HttpResponse<String> res;
    try {
      res = outerHttpClient.send(req, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      log.atInfo()
          .addKeyValue("endpoint", endpoint)
          .setCause(e)
          .log("failed to reach Information Service at '{}': {}", endpoint, e.getMessage());
      return new RecordStatus(Result.ERROR, "unreachable (%s)".formatted(e.getMessage()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InformationServiceException(
          "interrupted while querying Information Service at '%s'".formatted(endpoint), e);
    }

    // status code mapping according to I_Information_Service getRecordStatus
    var status = res.statusCode();
    if (status == 204) {
      return new RecordStatus(Result.ACTIVATED, "204");
    }
    if (status == 404) {
      return new RecordStatus(Result.NO_HEALTH_RECORD, "404");
    }

    var error = parseError(res.body());
    var description = "%d %s".formatted(status, errorCodeOf(error)).strip();
    if (status == 400) {
      logMalformedRequest(req, res, error, insurantId);
      return new RecordStatus(Result.ERROR, description);
    }
    if (status == 409) {
      logErrorResponse(res, error, insurantId, "health record is not in state ACTIVATED");
      return new RecordStatus(Result.STATUS_MISMATCH, description);
    }
    logErrorResponse(res, error, insurantId, "Information Service returned an error");
    return new RecordStatus(Result.ERROR, description);
  }

  private void logErrorResponse(
      HttpResponse<String> res, ErrorType error, String insurantId, String message) {
    log.atInfo()
        .addKeyValue("endpoint", res.uri())
        .addKeyValue("insurantId", insurantId)
        .addKeyValue("status", res.statusCode())
        .addKeyValue("errorCode", errorCodeOf(error))
        .addKeyValue("errorDetail", error != null ? error.errorDetail() : null)
        .log(
            "{} at '{}': status={}, errorCode={}",
            message,
            res.uri(),
            res.statusCode(),
            errorCodeOf(error));
  }

  /**
   * A '400 malformedRequest' means our request did not match the Information Service schema, so
   * retrying won't help. Log everything we know about the request and response to debug it.
   */
  private void logMalformedRequest(
      HttpRequest req, HttpResponse<String> res, ErrorType error, String insurantId) {
    log.atError()
        .addKeyValue("endpoint", res.uri())
        .addKeyValue("request.method", req.method())
        .addKeyValue("request.uri", req.uri())
        .addKeyValue("request.header.x-useragent", USER_AGENT)
        .addKeyValue("request.header.x-insurantid", insurantId)
        .addKeyValue("request.insurantId.length", insurantId == null ? null : insurantId.length())
        .addKeyValue(
            "request.insurantId.matchesKvnrFormat",
            insurantId != null && KVNR_FORMAT.matcher(insurantId).matches())
        .addKeyValue("response.status", res.statusCode())
        .addKeyValue("response.version", res.version())
        .addKeyValue("response.headers", loggableHeaders(res))
        .addKeyValue("response.body", truncate(res.body()))
        .addKeyValue("errorCode", errorCodeOf(error))
        .addKeyValue("errorDetail", error != null ? error.errorDetail() : null)
        .log(
            "Information Service rejected getRecordStatus as malformed at '{}': status={},"
                + " errorCode={}, errorDetail={}",
            res.uri(),
            res.statusCode(),
            errorCodeOf(error),
            error != null ? error.errorDetail() : null);
  }

  private static Map<String, List<String>> loggableHeaders(HttpResponse<?> res) {
    var headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
    headers.putAll(res.headers().map());
    // may carry session credentials
    headers.remove("set-cookie");
    return headers;
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
      return null;
    }
  }

  private static String truncate(String value) {
    if (value == null || value.length() <= MAX_LOGGED_BODY_LENGTH) {
      return value;
    }
    return value.substring(0, MAX_LOGGED_BODY_LENGTH) + "...(truncated)";
  }

  private enum Result {
    ACTIVATED,
    NO_HEALTH_RECORD,
    STATUS_MISMATCH,
    ERROR
  }

  private record RecordStatus(Result result, String description) {}

  /** Error body of the Information Service, see I_Information_Service 'ErrorType'. */
  record ErrorType(
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
