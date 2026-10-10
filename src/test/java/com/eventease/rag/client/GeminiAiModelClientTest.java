package com.eventease.rag.client;

import com.eventease.rag.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiAiModelClientTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private RagProperties ragProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getGemini().setApiKey("test-dummy-api-key");
        ragProperties.getGemini().setModel("gemini-3.8-flash");
        ragProperties.getGemini().setTimeoutSeconds(60);
        ragProperties.getGemini().setConnectTimeoutSeconds(10);
    }

    @Test
    @DisplayName("Should report isConfigured false when API key is empty or unconfigured")
    void shouldReportUnconfiguredWhenKeyMissing() {
        ragProperties.getGemini().setApiKey("");
        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        assertFalse(client.isConfigured());

        AiModelResponse response = client.generateAnswer("sys", "prompt");
        assertFalse(response.isSuccessful());
        assertTrue(response.getErrorMessage().contains("Gemini API key is not configured"));
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    @DisplayName("Should report isConfigured false when disabled")
    void shouldReportUnconfiguredWhenDisabled() {
        ragProperties.getGemini().setEnabled(false);
        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        assertFalse(client.isConfigured());
        AiModelResponse response = client.generateAnswer("sys", "prompt");
        assertFalse(response.isSuccessful());
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    @DisplayName("Should successfully parse response text from Gemini API with gemini-3.8-flash")
    void shouldParseGeminiResponseSuccessfully() throws IOException, InterruptedException {
        String jsonResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "Registration is free for all attendees." }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
            """;

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(jsonResponse);
        doReturn(mockHttpResponse).when(mockHttpClient).send(requestCaptor.capture(), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        assertTrue(client.isConfigured());
        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertTrue(response.isSuccessful());
        assertEquals("Registration is free for all attendees.", response.getText());
        assertEquals("gemini-3.8-flash", response.getModelName());

        // Verify request URI uses correct endpoint format without api key in query parameters
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", capturedRequest.uri().toString());
        assertNull(capturedRequest.uri().getQuery(), "URI must not contain query parameters like ?key=");
        assertFalse(capturedRequest.uri().toString().contains("test-dummy-api-key"), "URI must not contain API key");

        // Verify API key is passed securely via x-goog-api-key header
        assertEquals("test-dummy-api-key", capturedRequest.headers().firstValue("x-goog-api-key").orElse(null));
        assertEquals("application/json", capturedRequest.headers().firstValue("Content-Type").orElse(null));
    }

    @Test
    @DisplayName("Should normalize model name and prevent duplicate models/ prefix in endpoint URI")
    void shouldNormalizeModelNameAndAvoidDuplicateModelsPrefix() throws IOException, InterruptedException {
        String jsonResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "Normalized model answer." }
                    ]
                  }
                }
              ]
            }
            """;

        // Test with "models/gemini-3.8-flash" prefix configured
        ragProperties.getGemini().setModel("models/gemini-3.8-flash");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(jsonResponse);
        doReturn(mockHttpResponse).when(mockHttpClient).send(requestCaptor.capture(), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);
        assertEquals("gemini-3.8-flash", client.getModelName());

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");
        assertTrue(response.isSuccessful());
        assertEquals("gemini-3.8-flash", response.getModelName());

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", capturedRequest.uri().toString());
        assertFalse(capturedRequest.uri().toString().contains("models/models/"), "URI must never contain duplicate models/ prefix");
    }

    @Test
    @DisplayName("Should handle HTTP 404 Model Not Found error gracefully with clear message")
    void shouldHandleHttp404ModelNotFoundGracefully() throws IOException, InterruptedException {
        when(mockHttpResponse.statusCode()).thenReturn(404);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertFalse(response.isSuccessful());
        assertTrue(response.getErrorMessage().contains("Gemini model not found (HTTP 404)"));
        assertTrue(response.getErrorMessage().contains("gemini-3.8-flash"));
        assertFalse(response.getErrorMessage().contains("test-dummy-api-key"), "Error message must never leak API key");
    }

    @Test
    @DisplayName("Should handle HTTP 400 and 403 Invalid API key or request format gracefully")
    void shouldHandleHttp400And403Gracefully() throws IOException, InterruptedException {
        when(mockHttpResponse.statusCode()).thenReturn(400);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertFalse(response.isSuccessful());
        assertTrue(response.getErrorMessage().contains("Invalid API key or model request format (HTTP 400)"));
        assertFalse(response.getErrorMessage().contains("test-dummy-api-key"));
    }

    @Test
    @DisplayName("Should handle HTTP 429 quota/rate limit status code gracefully")
    void shouldHandleHttpErrorsGracefully() throws IOException, InterruptedException {
        when(mockHttpResponse.statusCode()).thenReturn(429);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertFalse(response.isSuccessful());
        assertTrue(response.getErrorMessage().contains("quota exceeded or rate limited"));
    }

    @Test
    @DisplayName("Should handle request timeout during answer generation gracefully")
    void shouldHandleRequestTimeoutGracefully() throws IOException, InterruptedException {
        doThrow(new HttpTimeoutException("Request timed out")).when(mockHttpClient).send(any(HttpRequest.class), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertFalse(response.isSuccessful());
        assertEquals("AI service request timed out.", response.getErrorMessage());
        assertEquals("gemini-3.8-flash", response.getModelName());
    }

    @Test
    @DisplayName("Should handle connection timeout connecting to host gracefully")
    void shouldHandleConnectionTimeoutGracefully() throws IOException, InterruptedException {
        doThrow(new HttpConnectTimeoutException("Connection timed out")).when(mockHttpClient).send(any(HttpRequest.class), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertFalse(response.isSuccessful());
        assertEquals("AI service connection timed out.", response.getErrorMessage());
        assertEquals("gemini-3.8-flash", response.getModelName());
    }

    @Test
    @DisplayName("Should construct exact canonical Gemini 3.8 Flash endpoint URL")
    void shouldConstructExactEndpointUrlForGemini38Flash() {
        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        // 1. Direct model name
        URI uriDirect = client.buildEndpointUri("gemini-3.8-flash");
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", uriDirect.toString());
        assertEquals("https", uriDirect.getScheme());
        assertEquals("generativelanguage.googleapis.com", uriDirect.getHost());
        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", uriDirect.getPath());
        assertNull(uriDirect.getQuery(), "URI must not have query params");

        // 2. Prefixed model name 'models/gemini-3.8-flash'
        URI uriPrefixed = client.buildEndpointUri("models/gemini-3.8-flash");
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", uriPrefixed.toString());
        assertFalse(uriPrefixed.toString().contains("models/models/"), "Must not duplicate models/ prefix");

        // 3. Trimmed model name
        URI uriPadded = client.buildEndpointUri("  gemini-3.8-flash  ");
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", uriPadded.toString());

        // 4. Null and empty defaults to gemini-3.8-flash
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", client.buildEndpointUri(null).toString());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", client.buildEndpointUri("").toString());
    }

    @Test
    @DisplayName("Should safely parse structured provider error response without leaking credentials")
    void shouldSafelyParseStructuredProviderErrorResponse() throws IOException, InterruptedException {
        String providerErrorJson = """
            {
              "error": {
                "code": 404,
                "message": "models/gemini-3.8-flash is not found for API version v1beta, or is not supported for generateContent. Call ListModels to see the list of available models and their supported methods.",
                "status": "NOT_FOUND"
              }
            }
            """;

        when(mockHttpResponse.statusCode()).thenReturn(404);
        when(mockHttpResponse.body()).thenReturn(providerErrorJson);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertFalse(response.isSuccessful());
        assertEquals("gemini-3.8-flash", response.getModelName());
        assertTrue(response.getErrorMessage().contains("Gemini model not found (HTTP 404): models/gemini-3.8-flash is not found for API version v1beta"));
        assertFalse(response.getErrorMessage().contains("test-dummy-api-key"), "Must not leak API key");
    }

    @Test
    @DisplayName("Should redact API key if inadvertently echoed in provider error message")
    void shouldRedactApiKeyIfEchoedInProviderError() throws IOException, InterruptedException {
        String errorWithKey = """
            {
              "error": {
                "code": 400,
                "message": "API key test-dummy-api-key was rejected by server",
                "status": "INVALID_ARGUMENT"
              }
            }
            """;

        when(mockHttpResponse.statusCode()).thenReturn(400);
        when(mockHttpResponse.body()).thenReturn(errorWithKey);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertFalse(response.isSuccessful());
        assertFalse(response.getErrorMessage().contains("test-dummy-api-key"), "API key must be redacted");
        assertTrue(response.getErrorMessage().contains("[REDACTED]"), "Redacted placeholder must be present");
    }

    @Test
    @DisplayName("Should handle malformed or non-JSON error body gracefully")
    void shouldHandleMalformedErrorBodyGracefully() throws IOException, InterruptedException {
        when(mockHttpResponse.statusCode()).thenReturn(404);
        when(mockHttpResponse.body()).thenReturn("<html><body>404 Not Found</body></html>");
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        GeminiAiModelClient client = new GeminiAiModelClient(ragProperties, objectMapper, mockHttpClient);

        AiModelResponse response = client.generateAnswer("System instruction", "User prompt");

        assertFalse(response.isSuccessful());
        assertTrue(response.getErrorMessage().contains("Gemini model not found (HTTP 404). Model name may be unsupported or invalid: gemini-3.8-flash"));
    }
}
