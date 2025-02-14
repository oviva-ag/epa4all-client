package com.oviva.telematik.vau.httpclient.internal.cert;

import java.security.cert.X509Certificate;
import java.util.List;

public record CertData(X509Certificate cert, X509Certificate ca, List<X509Certificate> chain) {}
