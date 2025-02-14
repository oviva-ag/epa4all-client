package com.oviva.telematik.epaapi;

import com.oviva.telematik.epaapi.internal.MtomConfigOutInterceptor;
import jakarta.xml.ws.soap.SOAPBinding;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.ext.logging.event.EventType;
import org.apache.cxf.ext.logging.event.LogEvent;
import org.apache.cxf.ext.logging.slf4j.Slf4jEventSender;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.WSAddressingFeature;
import org.slf4j.event.Level;
import telematik.ws.phr.wsdl.IDocumentManagementPortType;

public class SoapClientFactory {

  private final ClientConfiguration configuration;

  public SoapClientFactory(ClientConfiguration configuration) {
    this.configuration = configuration;
  }

  public IDocumentManagementPortType getIDocumentManagementPort(URI endpointAddress) {
    return getClientProxyImpl(IDocumentManagementPortType.class, endpointAddress);
  }

  private <T> T getClientProxyImpl(Class<T> portType, URI endpointAddress) {

    var jaxWsProxyFactory = newJaxWsProxyFactoryBean(portType, endpointAddress);

    T proxy = jaxWsProxyFactory.create(portType);

    var client = ClientProxy.getClient(proxy);

    enableThreadLocalRequestContext(client);
    configureHttpClient(client);

    return proxy;
  }

  private <T> JaxWsProxyFactoryBean newJaxWsProxyFactoryBean(
      Class<T> portType, URI endpointAddress) {

    final JaxWsProxyFactoryBean jaxWsProxyFactory = new JaxWsProxyFactoryBean();
    jaxWsProxyFactory.setBindingId(SOAPBinding.SOAP12HTTP_BINDING);
    jaxWsProxyFactory.setServiceClass(portType);
    jaxWsProxyFactory.setAddress(endpointAddress.toString());
    jaxWsProxyFactory.getFeatures().add(newLoggingFeature());
    jaxWsProxyFactory.getFeatures().add(new WSAddressingFeature());

    // A_14418-01: MUST use MTOM
    jaxWsProxyFactory.getOutInterceptors().add(new MtomConfigOutInterceptor());

    return jaxWsProxyFactory;
  }

  private void enableThreadLocalRequestContext(Client client) {
    // https://cxf.apache.org/faq.html#FAQ-AreJAX-WSclientproxiesthreadsafe?
    client.getRequestContext().put("thread.local.request.context", "true");
  }

  private void configureHttpClient(Client client) {

    var httpConduit = (HTTPConduit) client.getConduit();

    var httpClientPolicy = new HTTPClientPolicy();
    httpClientPolicy.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
    httpClientPolicy.setReceiveTimeout(Duration.ofSeconds(20).toMillis());

    // our VAU tunnel cannot handle chunking :)
    httpClientPolicy.setAllowChunking(false);

    httpConduit.setClient(httpClientPolicy);

    configureProxy(httpConduit);
  }

  private void configureProxy(HTTPConduit httpConduit) {
    Optional.ofNullable(configuration.proxyAddress())
        .ifPresent(
            pa -> {
              httpConduit.getClient().setProxyServer(Objects.requireNonNull(pa.getHostString()));
              httpConduit.getClient().setProxyServerPort(pa.getPort());
            });
  }

  private LoggingFeature newLoggingFeature() {
    final var feature = new LoggingFeature();
    final var sender =
        new Slf4jEventSender() {
          @Override
          protected String getLogMessage(LogEvent event) {
            var buf = new StringBuilder().append("\n");
            if (List.of(EventType.REQ_IN, EventType.REQ_OUT).contains(event.getType())) {
              buf.append(event.getHttpMethod()).append(" ").append(event.getAddress()).append("\n");
            } else {
              buf.append(event.getResponseCode())
                  .append(" ")
                  .append(event.getAddress())
                  .append("\n");
            }
            event
                .getHeaders()
                .forEach((key, value) -> buf.append(key).append(": ").append(value).append("\n"));
            return buf.append("\n").append(event.getPayload()).toString();
          }
        };
    sender.setLoggingLevel(Level.DEBUG);
    feature.setSender(sender);
    feature.setPrettyLogging(true);
    feature.setLogBinary(true);
    feature.setLogMultipart(true);
    return feature;
  }
}
