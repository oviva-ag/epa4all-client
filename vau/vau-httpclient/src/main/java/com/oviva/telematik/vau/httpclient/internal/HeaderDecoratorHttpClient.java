package com.oviva.telematik.vau.httpclient.internal;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class HeaderDecoratorHttpClient implements HttpClient {

  private final HttpClient delegate;
  private final List<HttpHeader> extraHeaders;

  public HeaderDecoratorHttpClient(HttpClient httpClient, List<HttpHeader> extraHeaders) {
    this.delegate = httpClient;
    this.extraHeaders = extraHeaders;
  }

  @Override
  public HttpResponse call(HttpRequest req) {

    var decorated = new ArrayList<>(extraHeaders);
    if (req.headers() != null) {
      for (HttpHeader h : req.headers()) {
        if (isExtraHeader(h)) {
          continue;
        }
        decorated.add(h);
      }
    }

    var newRequest = new HttpRequest(req.uri(), req.method(), decorated, req.body());
    return delegate.call(newRequest);
  }

  private boolean isExtraHeader(HttpHeader h) {
    return extraHeaders.stream().anyMatch(n -> n.name().equalsIgnoreCase(h.name()));
  }
}
