package com.oviva.telematik.vau.httpclient;

import java.net.URI;

public interface VauClientFactory {

  HttpClient connect(URI vauBaseUri);
}
