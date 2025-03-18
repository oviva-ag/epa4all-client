package com.oviva.telematik.vau.httpclient;

import java.util.List;

@SuppressWarnings("java:S6218")
public record HttpResponse(int status, List<HttpHeader> headers, byte[] body) {}
