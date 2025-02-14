package com.oviva.telematik.vau.epa4all.client.authz.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NonceResponse(@JsonProperty("nonce") String nonce) {}
