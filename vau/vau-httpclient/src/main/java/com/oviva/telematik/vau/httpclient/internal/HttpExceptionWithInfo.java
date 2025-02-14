package com.oviva.telematik.vau.httpclient.internal;

import com.oviva.telematik.vau.httpclient.HttpClient;
import java.net.URI;

public class HttpExceptionWithInfo extends HttpClient.HttpException {

  private final int status;
  private final String method;
  private final URI uri;

  public HttpExceptionWithInfo(int status, String method, URI uri, String message) {
    super(message);
    this.status = status;
    this.method = method;
    this.uri = uri;
  }

  public HttpExceptionWithInfo(String method, URI uri, String message, Exception cause) {
    super(message, cause);
    this.status = 0;
    this.method = method;
    this.uri = uri;
  }

  @Override
  public String getMessage() {
    var sb = new StringBuilder("http request failed: ").append(super.getMessage());
    if (method != null && uri != null) {
      sb.append(" '").append(method).append(" ").append(uri).append("'");
    }

    if (status > 0) {
      sb.append(" status=").append(status);
    }

    return sb.toString();
  }

  public int status() {
    return status;
  }

  public String method() {
    return method;
  }

  public URI uri() {
    return uri;
  }
}
