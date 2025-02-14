package com.oviva.telematik.vau.httpclient.internal;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;

public class LoggingHttpClient implements HttpClient {

  private final Logger logger;
  private final HttpClient delegate;

  public LoggingHttpClient(HttpClient delegate, Logger logger) {
    this.delegate = delegate;
    this.logger = logger;
  }

  @Override
  public HttpResponse call(HttpRequest req) {

    if (!logger.isDebugEnabled()) {
      return delegate.call(req);
    }

    var raw = HttpCodec.encode(req);

    logger.atDebug().log(
        "> http request: {} {} \n===\n{}===",
        req.method(),
        req.uri(),
        new String(raw, StandardCharsets.UTF_8));

    var res = delegate.call(req);

    logger.atDebug().log(
        "< http response: {} {} \n===\n{}===", req.method(), req.uri(), stringify(res));

    return res;
  }

  private String stringify(HttpResponse response) {
    var sb = new StringBuilder();
    sb.append("status=").append(response.status()).append('\n');
    response.headers().stream()
        .map(h -> "%s: %s\n".formatted(h.name(), h.value()))
        .forEach(sb::append);
    sb.append('\n');

    if (response.body() != null) {
      sb.append(new String(response.body(), StandardCharsets.UTF_8));
    }
    return sb.toString();
  }
}
