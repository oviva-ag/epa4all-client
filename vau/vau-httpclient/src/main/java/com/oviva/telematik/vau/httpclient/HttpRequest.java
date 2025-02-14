package com.oviva.telematik.vau.httpclient;

import com.oviva.telematik.vau.httpclient.internal.HttpCodec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SuppressWarnings("java:S6218")
public record HttpRequest(URI uri, String method, List<HttpHeader> headers, byte[] body) {
  @Override
  public String toString() {
    return new String(HttpCodec.encode(this), StandardCharsets.UTF_8);
  }
}
