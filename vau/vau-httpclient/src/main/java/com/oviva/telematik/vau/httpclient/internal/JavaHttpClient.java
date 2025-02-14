package com.oviva.telematik.vau.httpclient.internal;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.stream.Stream;

public class JavaHttpClient implements HttpClient {

  private final java.net.http.HttpClient httpClient;

  JavaHttpClient(java.net.http.HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public static JavaHttpClient from(java.net.http.HttpClient client) {
    return new JavaHttpClient(client);
  }

  @Override
  public com.oviva.telematik.vau.httpclient.HttpResponse call(
      com.oviva.telematik.vau.httpclient.HttpRequest req) {

    var builder = HttpRequest.newBuilder().uri(req.uri());

    Stream.ofNullable(req.headers())
        .flatMap(List::stream)
        .forEach(h -> builder.header(h.name(), h.value()));

    if (req.body() == null || req.body().length == 0) {
      builder.method(req.method(), BodyPublishers.noBody());
    } else {
      builder.method(req.method(), BodyPublishers.ofByteArray(req.body()));
    }

    try {
      var res = httpClient.send(builder.build(), BodyHandlers.ofByteArray());
      return toResponse(res);
    } catch (IOException e) {
      throw httpFailCausedBy(req.method(), req.uri(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return null;
  }

  private com.oviva.telematik.vau.httpclient.HttpResponse toResponse(
      HttpResponse<byte[]> response) {

    var headers =
        response.headers().map().entrySet().stream()
            .map(e -> new HttpHeader(e.getKey(), e.getValue().get(0)))
            .toList();

    return new com.oviva.telematik.vau.httpclient.HttpResponse(
        response.statusCode(), headers, response.body());
  }

  public static HttpException httpFailBadStatus(String method, URI uri, int status) {
    return new HttpExceptionWithInfo(status, method, uri, "bad status");
  }

  public static HttpException httpFailCausedBy(String method, URI uri, Exception cause) {
    return new HttpExceptionWithInfo(method, uri, "http request failed", cause);
  }
}
