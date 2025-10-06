package com.oviva.telematik.epa4all.client;

import com.oviva.epa.client.KonnektorService;
import com.oviva.telematik.epa4all.client.internal.Epa4AllClientFactory;
import com.oviva.telematik.epa4all.client.internal.TelematikTrustRoots;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.Objects;

/** Builder for Epa4AllClientFactory. */
public class Epa4AllClientFactoryBuilder {

  private KonnektorService konnektorService;
  private InetSocketAddress konnektorProxyAddress;
  private KeyStore trustStore;

  private Environment environment;
  private String telentikId;

  private Epa4AllClientFactoryBuilder() {}

  public static Epa4AllClientFactoryBuilder newBuilder() {
    return new Epa4AllClientFactoryBuilder();
  }

  @NonNull
  public Epa4AllClientFactoryBuilder konnektorService(@NonNull KonnektorService konnektorService) {
    this.konnektorService =
        Objects.requireNonNull(konnektorService, "konnektorService must not be null");
    return this;
  }

  @NonNull
  public Epa4AllClientFactoryBuilder konnektorProxyAddress(
      InetSocketAddress konnektorProxyAddress) {
    this.konnektorProxyAddress = konnektorProxyAddress;
    return this;
  }

  @NonNull
  public Epa4AllClientFactoryBuilder environment(Environment environment) {
    this.environment = Objects.requireNonNull(environment, "environment must not be null");
    return this;
  }

  @NonNull
  public Epa4AllClientFactoryBuilder trustStore(KeyStore trustStore) {
    this.trustStore = Objects.requireNonNull(trustStore, "trustManager must not be null");
    return this;
  }

  @NonNull
  public Epa4AllClientFactoryBuilder telmatikId(String telmatikId) {
    this.telentikId = telmatikId;
    return this;
  }

  @NonNull
  public Epa4AllClientFactory build() {
    Objects.requireNonNull(konnektorService, "konnektorService must be set");
    Objects.requireNonNull(environment, "environment must be set");

    var actualTrustStore = determineTrustStore(environment == Environment.PU, trustStore);
    Objects.requireNonNull(actualTrustStore, "trustStore must be set");

    return Epa4AllClientFactory.create(
        konnektorService, konnektorProxyAddress, environment, actualTrustStore, telentikId);
  }

  private KeyStore determineTrustStore(boolean isPu, KeyStore providedTrustStore) {
    if (providedTrustStore != null) {
      return providedTrustStore;
    } else if (isPu) {
      return TelematikTrustRoots.loadPuTruststore();
    } else {
      return TelematikTrustRoots.loadRuTruststore();
    }
  }
}
