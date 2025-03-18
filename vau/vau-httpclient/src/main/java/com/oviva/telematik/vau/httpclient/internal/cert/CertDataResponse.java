package com.oviva.telematik.vau.httpclient.internal.cert;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A_24957 */
@SuppressWarnings("java:S6218")
public record CertDataResponse(
    @JsonProperty("cert") byte[] cert,
    @JsonProperty("ca") byte[] ca,
    @JsonProperty("rca_chain") List<byte[]> rcaChain) {}
