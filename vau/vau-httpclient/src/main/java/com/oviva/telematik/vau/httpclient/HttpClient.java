package com.oviva.telematik.vau.httpclient;

/** Very basic interface for an HttpClient */
public interface HttpClient {

  HttpResponse call(HttpRequest req);

  class HttpException extends RuntimeException {
    public HttpException(String message) {
      super(message);
    }

    public HttpException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
