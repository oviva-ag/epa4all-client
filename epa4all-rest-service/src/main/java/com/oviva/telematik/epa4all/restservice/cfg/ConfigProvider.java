package com.oviva.telematik.epa4all.restservice.cfg;

import java.util.Optional;

public interface ConfigProvider {
  Optional<String> get(String name);
}
