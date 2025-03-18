package com.oviva.telematik.vau.httpclient.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oviva.telematik.vau.httpclient.HttpClient;
import com.oviva.telematik.vau.httpclient.HttpHeader;
import com.oviva.telematik.vau.httpclient.HttpRequest;
import com.oviva.telematik.vau.httpclient.HttpResponse;
import de.gematik.vau.lib.VauClientStateMachine;
import de.gematik.vau.lib.exceptions.VauProtocolException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionFactoryTest {

  @Mock private HttpClient mockHttpClient;

  @Mock private VauClientStateMachine mockVauClientStateMachine;

  @Mock private SignedPublicKeysTrustValidatorFactory mockTrustValidatorFactory;

  private ConnectionFactory connectionFactory;

  private final String userAgent = "TestUserAgent/1.0.0";
  private final URI testVauUri = URI.create("https://test.example.com");

  @BeforeEach
  void setUp() {
    connectionFactory = new ConnectionFactory(mockHttpClient, userAgent, mockTrustValidatorFactory);
  }

  @Test
  void constructor_shouldCreateWithProperConfiguration() {
    // Given + When from setUp

    // Not much to verify directly since fields are private
    // We'll verify behavior through the connect method tests
    assertNotNull(connectionFactory);
  }

  @Test
  void connect_shouldReturnHttpClient() {
    // Given
    // Create mocks for the messages in the handshake
    byte[] mockMsg1 = "msg1".getBytes();
    byte[] mockMsg3 = "msg3".getBytes();
    byte[] mockMsg4Body = "msg4".getBytes();

    // Setup VauClientStateMachine behavior
    when(mockVauClientStateMachine.generateMessage1()).thenReturn(mockMsg1);
    when(mockVauClientStateMachine.receiveMessage2(any())).thenReturn(mockMsg3);
    doNothing().when(mockVauClientStateMachine).receiveMessage4(any());

    // Setup the factory to return our mocked VauClientStateMachine
    when(mockTrustValidatorFactory.create(any())).thenReturn(mockVauClientStateMachine);

    // Mock HTTP responses - we need to be specific about the URIs and sequence
    HttpResponse mockMsg2Response =
        new HttpResponse(
            200, List.of(new HttpHeader("VAU-CID", "/test-cid")), "msg2-body".getBytes());

    HttpResponse mockMsg4Response = new HttpResponse(200, Collections.emptyList(), mockMsg4Body);

    // Setup HTTP client to return expected responses in sequence
    // First setup a general response pattern for all requests
    when(mockHttpClient.call(any())).thenReturn(mockMsg2Response, mockMsg4Response);

    // When
    HttpClient result = connectionFactory.connect(testVauUri);

    // Then
    assertNotNull(result, "The returned HttpClient should not be null");

    // Verify factory was called to create the client state machine with correct URI
    verify(mockTrustValidatorFactory).create(testVauUri);

    // Verify the expected methods were called on the VauClientStateMachine
    verify(mockVauClientStateMachine).generateMessage1();
    verify(mockVauClientStateMachine).receiveMessage2(any());
    verify(mockVauClientStateMachine).receiveMessage4(any());

    // Verify HTTP client was called twice with appropriate requests
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(mockHttpClient, times(2)).call(requestCaptor.capture());

    List<HttpRequest> capturedRequests = requestCaptor.getAllValues();
    assertEquals(2, capturedRequests.size(), "Should have made exactly 2 HTTP requests");

    // Verify first request (msg1)
    HttpRequest firstRequest = capturedRequests.get(0);
    assertNotNull(firstRequest, "First request should not be null");
    assertTrue(
        firstRequest.uri().toString().endsWith("/VAU"), "First request should be to VAU endpoint");

    // Verify second request (msg3)
    HttpRequest secondRequest = capturedRequests.get(1);
    assertNotNull(secondRequest, "Second request should not be null");
    assertTrue(
        secondRequest.uri().toString().contains("/test-cid"),
        "Second request should be to the CID-specific endpoint");
  }

  @Test
  void connect_shouldThrowExceptionWhenCidIsNull() {
    // Given
    byte[] mockMsg1 = "msg1".getBytes();

    // Setup VauClientStateMachine behavior
    when(mockVauClientStateMachine.generateMessage1()).thenReturn(mockMsg1);

    // Setup the factory to return our mocked VauClientStateMachine
    when(mockTrustValidatorFactory.create(any())).thenReturn(mockVauClientStateMachine);

    // Mock HTTP response without VAU-CID header
    HttpResponse mockResponse =
        new HttpResponse(
            200,
            Collections.emptyList(), // No VAU-CID header
            "msg2-body".getBytes());

    when(mockHttpClient.call(any())).thenReturn(mockResponse);

    // When & Then
    assertThrows(VauProtocolException.class, () -> connectionFactory.connect(testVauUri));

    // Verify the factory was called
    verify(mockTrustValidatorFactory).create(any());
  }

  @Test
  void connect_shouldThrowExceptionWhenCidIsTooLong() {
    // Given
    byte[] mockMsg1 = "msg1".getBytes();

    // Setup VauClientStateMachine behavior
    when(mockVauClientStateMachine.generateMessage1()).thenReturn(mockMsg1);

    // Setup the factory to return our mocked VauClientStateMachine
    when(mockTrustValidatorFactory.create(any())).thenReturn(mockVauClientStateMachine);

    // Create a CID that exceeds the maximum length (200 bytes)
    StringBuilder longCid = new StringBuilder("/");
    for (int i = 0; i < 200; i++) {
      longCid.append("a");
    }

    // Mock HTTP response with too long VAU-CID header
    HttpResponse mockResponse =
        new HttpResponse(
            200, List.of(new HttpHeader("VAU-CID", longCid.toString())), "msg2-body".getBytes());

    when(mockHttpClient.call(any())).thenReturn(mockResponse);

    // When & Then
    assertThrows(VauProtocolException.class, () -> connectionFactory.connect(testVauUri));

    // Verify the factory was called
    verify(mockTrustValidatorFactory).create(any());
  }

  @Test
  void connect_shouldThrowExceptionWhenCidHasInvalidFormat() {
    // Given
    byte[] mockMsg1 = "msg1".getBytes();

    // Setup VauClientStateMachine behavior
    when(mockVauClientStateMachine.generateMessage1()).thenReturn(mockMsg1);

    // Setup the factory to return our mocked VauClientStateMachine
    when(mockTrustValidatorFactory.create(any())).thenReturn(mockVauClientStateMachine);

    // Mock HTTP response with invalid VAU-CID header format
    HttpResponse mockResponse =
        new HttpResponse(
            200,
            List.of(new HttpHeader("VAU-CID", "invalid*cid")), // Contains invalid character
            "msg2-body".getBytes());

    when(mockHttpClient.call(any())).thenReturn(mockResponse);

    // When & Then
    assertThrows(VauProtocolException.class, () -> connectionFactory.connect(testVauUri));

    // Verify the factory was called
    verify(mockTrustValidatorFactory).create(any());
  }

  @Test
  void connect_shouldThrowExceptionWhenHttpStatusIsNot200() {
    // Given
    byte[] mockMsg1 = "msg1".getBytes();

    // Setup VauClientStateMachine behavior
    when(mockVauClientStateMachine.generateMessage1()).thenReturn(mockMsg1);

    // Setup the factory to return our mocked VauClientStateMachine
    when(mockTrustValidatorFactory.create(any())).thenReturn(mockVauClientStateMachine);

    // Mock HTTP response with non-200 status
    HttpResponse mockResponse =
        new HttpResponse(404, Collections.emptyList(), "not found".getBytes());

    when(mockHttpClient.call(any())).thenReturn(mockResponse);

    // When & Then
    assertThrows(HttpExceptionWithInfo.class, () -> connectionFactory.connect(testVauUri));

    // Verify the factory was called
    verify(mockTrustValidatorFactory).create(any());
  }

  @Test
  void connect_shouldCorrectlyParseUserAgentHeader() {
    // Given
    // Setup VauClientStateMachine behavior
    when(mockVauClientStateMachine.generateMessage1()).thenReturn("msg1".getBytes());
    when(mockVauClientStateMachine.receiveMessage2(any())).thenReturn("msg3".getBytes());

    // Setup the factory to return our mocked VauClientStateMachine
    when(mockTrustValidatorFactory.create(any())).thenReturn(mockVauClientStateMachine);

    // Mock HTTP responses for successful connection
    HttpResponse mockMsg2Response =
        new HttpResponse(
            200, List.of(new HttpHeader("VAU-CID", "/test-cid")), "msg2-body".getBytes());

    HttpResponse mockMsg4Response =
        new HttpResponse(200, Collections.emptyList(), "msg4".getBytes());

    // Setup HTTP client to return expected responses
    when(mockHttpClient.call(any())).thenReturn(mockMsg2Response, mockMsg4Response);

    // Capture the request to verify headers
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

    // When
    connectionFactory.connect(testVauUri);

    // Then
    verify(mockHttpClient, atLeastOnce()).call(requestCaptor.capture());

    // Verify user agent headers were correctly set
    List<HttpRequest> capturedRequests = requestCaptor.getAllValues();
    HttpRequest firstRequest = capturedRequests.get(0);

    boolean hasUserAgentHeader =
        firstRequest.headers().stream()
            .anyMatch(
                h ->
                    ("User-Agent".equals(h.name()) || "x-useragent".equals(h.name()))
                        && userAgent.equals(h.value()));

    assertTrue(hasUserAgentHeader, "Request should contain proper User-Agent header");
  }
}
