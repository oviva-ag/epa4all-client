package com.oviva.telematik.vau.epa4all.client.authz.internal;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class UriQueryUtil {

  private UriQueryUtil() {}

  public static Map<String, String> parse(URI url) {

    return Optional.ofNullable(url.getRawQuery()).map(q -> q.split("&")).stream()
        .flatMap(Arrays::stream)
        .map(UriQueryUtil::parseParamPair)
        .flatMap(Optional::stream)
        .collect(Collectors.toMap(p -> p[0], p -> p[1]));
  }

  private static Optional<String[]> parseParamPair(String pair) {
    if (!pair.contains("=")) {
      return Optional.empty();
    }
    var splits = pair.split("=");
    if (splits.length != 2) {
      return Optional.empty();
    }

    var name = URLDecoder.decode(splits[0], StandardCharsets.UTF_8);
    var value = URLDecoder.decode(splits[1], StandardCharsets.UTF_8);

    return Optional.of(new String[] {name, value});
  }
}
