package com.oviva.telematik.vau.proxy;

import java.util.Optional;

public interface ConfigProvider {
  Optional<String> get(String name);
}
