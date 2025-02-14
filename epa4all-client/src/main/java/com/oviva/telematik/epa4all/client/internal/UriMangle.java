package com.oviva.telematik.epa4all.client.internal;

import java.net.URI;

public class UriMangle {

  private UriMangle() {}

  public static URI downgradeHttpsUri(URI u) {

    var port = u.getPort();
    var scheme = u.getScheme();
    if ("http".equals(scheme)) {
      return u;
    }

    if (((port == 443) || (port == -1)) && "https".equals(scheme)) {
      return URI.create("http://%s%s".formatted(u.getHost(), u.getPath()));
    }

    throw new IllegalArgumentException("failed downgrading uri=%s".formatted(u));
  }
}
