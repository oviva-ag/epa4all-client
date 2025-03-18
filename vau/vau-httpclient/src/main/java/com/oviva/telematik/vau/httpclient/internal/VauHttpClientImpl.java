package com.oviva.telematik.vau.httpclient.internal;

import static java.util.function.Predicate.*;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This is a rather basic HTTP/1.1 client specifically to run HTTP over a VAU tunnel. */
public class VauHttpClientImpl implements HttpClient {

  private static final Logger log = LoggerFactory.getLogger("vau-httpclient");

  private final Connection conn;

  public VauHttpClientImpl(Connection conn) {
    this.conn = conn;
  }

  @Override
  public HttpResponse call(HttpRequest req) {
    // https://datatracker.ietf.org/doc/html/rfc2616

    List<HttpHeader> headers = new ArrayList<>();
    if (req.headers() != null) {
      headers.addAll(req.headers());
    }

    // we don't support chunking and always send all at once, so strip the header and set a proper
    // length
    var length = req.body() != null ? req.body().length : 0;
    headers = adjustContentLengthHeader(headers, length);

    req = new HttpRequest(req.uri(), req.method(), headers, req.body());

    var requestBytes = HttpCodec.encode(req);

    if (log.isDebugEnabled()) {
      log.atDebug().log(
          "> http request: {} {} \n===\n{}===",
          req.method(),
          req.uri(),
          new String(requestBytes, StandardCharsets.UTF_8));
    }

    var rxBytes = conn.call(requestBytes);

    if (log.isDebugEnabled()) {
      log.atDebug().log(
          "< http response in VAU tunnel: \n===\n{}\n===",
          new String(rxBytes != null ? rxBytes : new byte[0], StandardCharsets.UTF_8));
    }

    return HttpCodec.decode(rxBytes);
  }

  private List<HttpHeader> adjustContentLengthHeader(List<HttpHeader> headers, int actualSize) {

    var newHeaders =
        headers.stream()
            .filter(this::hasValidHeaderName)
            .filter(not(this::isContentLength))
            .filter(not(this::isTransferEncodingChunked))
            .collect(Collectors.toCollection(ArrayList<HttpHeader>::new));

    newHeaders.add(new HttpHeader("Content-Length", String.valueOf(actualSize)));

    return newHeaders;
  }

  private boolean hasValidHeaderName(HttpHeader h) {
    return h != null && h.name() != null;
  }

  private boolean isContentLength(HttpHeader h) {
    return h.name().equalsIgnoreCase("Content-Length");
  }

  private boolean isTransferEncodingChunked(HttpHeader h) {
    if (!h.name().equalsIgnoreCase("Transfer-Encoding")) {
      return false;
    }
    var v = h.value().trim();
    return h.name().equalsIgnoreCase("Transfer-Encoding") && "chunked".equalsIgnoreCase(v);
  }
}
