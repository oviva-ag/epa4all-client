package com.oviva.telematik.epaapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.junit.jupiter.api.Test;

class SoapClientFactoryTest {

  @Test
  void getIDocumentManagementPort_shouldConfigureTimeouts() {
    var factory = new SoapClientFactory(new ClientConfiguration(null));

    var port =
        factory.getIDocumentManagementPort(URI.create("http://localhost/I_Document_Management"));

    var httpConduit = (HTTPConduit) ClientProxy.getClient(port).getConduit();
    assertEquals(10_000, httpConduit.getClient().getConnectionTimeout());
    assertEquals(60_000, httpConduit.getClient().getReceiveTimeout());
  }
}
