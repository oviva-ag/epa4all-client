package com.oviva.telematik.vau.proxy;

import com.oviva.telematik.vau.httpclient.*;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VauProxyHandler implements HttpHandler {

  private static final AttachmentKey<HttpClient> UPSTREAM_KEY =
      AttachmentKey.create(HttpClient.class);
  private static final Logger log = LoggerFactory.getLogger(VauProxyHandler.class);

  private final ConcurrentHashMap<CacheKey, HttpClient> clientCache = new ConcurrentHashMap<>();

  private final VauClientFactory vauClientFactory;

  public VauProxyHandler(VauClientFactory vauClientFactory) {
    this.vauClientFactory = vauClientFactory;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {

    var supportedMethods = List.of(Methods.GET, Methods.POST, Methods.PUT, Methods.DELETE);
    if (!supportedMethods.contains(exchange.getRequestMethod())) {
      exchange.setStatusCode(405).endExchange();
      return;
    }

    var blocking = exchange.startBlocking();
    if (exchange.isInIoThread()) {
      exchange.dispatch(this);
      return;
    }

    blocking
        .getReceiver()
        .receiveFullBytes(
            (fbex, requestBytes) -> {

              // open or re-use a VAU tunnel
              var httpClient = getOrCreateUpstream(fbex);
              var req = prepareRequest(fbex, requestBytes);
              HttpResponse res = null;
              try {
                res = httpClient.call(req);
              } catch (HttpClient.HttpException e) {
                log.atDebug()
                    .setCause(e)
                    .log("upstream VAU call failed: %s".formatted(e.getMessage()));
                fbex.setStatusCode(StatusCodes.BAD_GATEWAY).endExchange();
                return;
              }
              sendResponse(fbex, res);
            });
  }

  private synchronized HttpClient getOrCreateUpstream(HttpServerExchange exchange) {

    var client = exchange.getAttachment(UPSTREAM_KEY);
    // TODO should we verify that the client is for the right upstream AND insurantId?
    if (client != null) {
      return client;
    }

    var requestUri = URI.create(exchange.getRequestURI());

    // IMPORTANT: upgrades to HTTPS -> this only works if the original one was downgraded! I.e. this
    // does not work for local tests.
    var upstreamEndpoint = URI.create("https://%s".formatted(requestUri.getHost()));
    var insurantId = exchange.getRequestHeaders().getFirst("x-insurantid");
    var key = new CacheKey(upstreamEndpoint, insurantId);

    return clientCache.computeIfAbsent(
        key,
        upstream -> {
          var newClient = vauClientFactory.connect(upstream.uri());
          exchange.putAttachment(UPSTREAM_KEY, newClient);
          return newClient;
        });
  }

  private void sendResponse(HttpServerExchange exchange, HttpResponse res) {

    for (var h : res.headers()) {
      exchange.getResponseHeaders().add(HttpString.tryFromString(h.name()), h.value());
    }

    exchange.setStatusCode(res.status());
    var body = res.body();
    if (body != null && body.length > 0) {
      exchange.getResponseSender().send(ByteBuffer.wrap(body));
    }
    exchange.endExchange();
  }

  private HttpRequest prepareRequest(HttpServerExchange exchange, byte[] body) {
    var method = exchange.getRequestMethod().toString();
    var headers = exchange.getRequestHeaders();

    var path = exchange.getRequestPath();

    // we just use the same path, no upstream host set
    var requestUri = URI.create(path);

    var requestHeaders = new ArrayList<HttpHeader>();
    for (var h : headers) {
      var name = h.getHeaderName().toString();
      for (var v : h) {
        requestHeaders.add(new HttpHeader(name, v));
      }
    }

    return new HttpRequest(requestUri, method, requestHeaders, body);
  }

  record CacheKey(URI uri, String insurantId) {}
}
