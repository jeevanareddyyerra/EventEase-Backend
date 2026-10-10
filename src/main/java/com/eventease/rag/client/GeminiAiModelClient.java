package com.eventease.rag.client;

import com.eventease.rag.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter client for Google Gemini generative AI model API.
 * Uses standard java.net.http.HttpClient with bounded timeouts and safe failure handling.
 * Never logs API keys, prompt bodies, or personal data.
 */
@Component
public class GeminiAiModelClient implements AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiModelClient.class);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiAiModelClient(RagProperties ragProperties, ObjectMapper objectMapper) {
        this(ragProperties, objectMapper, buildHttpClient(ragProperties));
    }

    private static HttpClient buildHttpClient(RagProperties ragProperties) {
        int connectTimeout = 10;
        if (ragProperties != null && ragProperties.getGemini() != null) {
            connectTimeout = Math.max(2, ragProperties.getGemini().getConnectTimeoutSeconds());
        }
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .build();
    }

    public GeminiAiModelClient(RagProperties ragProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
    }

    @Override
    public boolean isConfigured() {
        if (ragProperties == null || ragProperties.getGemini() == null) {
            return false;
        }
        RagProperties.GeminiProperties gemini = ragProperties.getGemini();
        String apiKey = gemini.getApiKey();
        return gemini.isEnabled() && apiKey != null && !apiKey.trim().isEmpty() && !apiKey.trim().equalsIgnoreCase("UNCONFIGURED");
    }

    /**
     * Normalizes a model identifier by stripping any leading 'models/' prefix and trimming whitespace.
     * Defaults to 'gemini-3.8-flash' if null or empty.
     */
    public String normalizeModelName(String model) {
        if (model == null || model.trim().isEmpty()) {
            return "gemini-3.8-flash";
        }
        String trimmed = model.trim();
        if (trimmed.startsWith("models/")) {
            trimmed = trimmed.substring("models/".length()).trim();
        }
        return trimmed.isEmpty() ? "gemini-3.8-flash" : trimmed;
    }

    /**
     * Constructs the canonical Gemini generateContent REST URI.
     * Endpoint format: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
     */
    public URI buildEndpointUri(String model) {
        String normalized = normalizeModelName(model);
        return URI.create(GEMINI_BASE_URL + normalized + ":generateContent");
    }

    /**
     * Safely parses provider error response body to extract error message/details without leaking sensitive tokens.
     */
    String extractProviderErrorMessage(String responseBody, String sensitiveToken) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                String message = errorNode.path("message").asText(null);
                if (message != null && !message.isBlank()) {
                    String clean = message.trim();
                    if (sensitiveToken != null && !sensitiveToken.isBlank() && clean.contains(sensitiveToken)) {
                        clean = clean.replace(sensitiveToken, "[REDACTED]");
                    }
                    return clean;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse provider error JSON: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public String getModelName() {
        if (ragProperties != null && ragProperties.getGemini() != null) {
            String m = ragProperties.getGemini().getModel();
            if (m != null && !m.trim().isEmpty()) {
                return normalizeModelName(m);
            }
        }
        return "gemini-3.8-flash";
    }

    @Override
    public AiModelResponse generateAnswer(String systemInstruction, String userPrompt) {
        String model = getModelName();
        String normalizedModel = normalizeModelName(model);

        if (!isConfigured()) {
            log.debug("Gemini AI client is not configured with an API key or is disabled.");
            return AiModelResponse.failure("Gemini API key is not configured or client is disabled.", normalizedModel);
        }

        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return AiModelResponse.failure("Prompt content is empty.", normalizedModel);
        }

        RagProperties.GeminiProperties gemini = ragProperties.getGemini();
        String apiKey = gemini.getApiKey().trim();
        int timeoutSeconds = Math.max(5, gemini.getTimeoutSeconds());
        int connectTimeoutSeconds = Math.max(2, gemini.getConnectTimeoutSeconds());

        try {
            // Build Gemini generateContent JSON payload per official Gemini API specification
            Map<String, Object> payload = new HashMap<>();

            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                payload.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction.trim()))
                ));
            }

            payload.put("contents", List.of(
                    Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userPrompt.trim()))
                    )
            ));

            payload.put("generationConfig", Map.of(
                    "temperature", gemini.getTemperature(),
                    "maxOutputTokens", 1024
            ));

            String requestJson = objectMapper.writeValueAsString(payload);

            // Construct canonical URI and pass API key via x-goog-api-key header
            URI uri = buildEndpointUri(normalizedModel);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            log.debug("Executing Gemini model inference call with model: {}", normalizedModel);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode != 200) {
                String providerError = extractProviderErrorMessage(response.body(), apiKey);
                if (providerError != null) {
                    log.warn("Gemini API returned HTTP {} for model '{}' with provider error: {}", statusCode, normalizedModel, providerError);
                } else {
                    log.warn("Gemini API returned non-200 HTTP status code: {} for model '{}'", statusCode, normalizedModel);
                }

                if (statusCode == 400 || statusCode == 403) {
                    String msg = providerError != null
                            ? "Invalid API key or model request format (HTTP " + statusCode + "): " + providerError
                            : "Invalid API key or model request format (HTTP " + statusCode + ").";
                    return AiModelResponse.failure(msg, normalizedModel);
                } else if (statusCode == 404) {
                    String msg = providerError != null
                            ? "Gemini model not found (HTTP 404): " + providerError
                            : "Gemini model not found (HTTP 404). Model name may be unsupported or invalid: " + normalizedModel;
                    return AiModelResponse.failure(msg, normalizedModel);
                } else if (statusCode == 429) {
                    String msg = providerError != null
                            ? "Gemini API quota exceeded (HTTP 429): " + providerError
                            : "Gemini API quota exceeded or rate limited (HTTP 429).";
                    return AiModelResponse.failure(msg, normalizedModel);
                } else {
                    String msg = providerError != null
                            ? "Gemini API service error (HTTP " + statusCode + "): " + providerError
                            : "Gemini API service error (HTTP " + statusCode + ").";
                    return AiModelResponse.failure(msg, normalizedModel);
                }
            }

            // Parse response safely
            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode parts = firstCandidate.path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    String generatedText = parts.get(0).path("text").asText();
                    if (generatedText != null && !generatedText.trim().isEmpty()) {
                        return AiModelResponse.success(generatedText.trim(), normalizedModel);
                    }
                }
            }

            log.warn("Gemini API returned response without text content in candidates");
            return AiModelResponse.failure("Model returned an empty response.", normalizedModel);

        } catch (java.net.http.HttpConnectTimeoutException e) {
            log.warn("Gemini API connection timed out after {} seconds connecting to host", connectTimeoutSeconds);
            return AiModelResponse.failure("AI service connection timed out.", model);
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Gemini API request timed out after {} seconds waiting for model response", timeoutSeconds);
            return AiModelResponse.failure("AI service request timed out.", model);
        } catch (Exception e) {
            log.error("Failed to execute Gemini API call: {}", e.getMessage());
            return AiModelResponse.failure("Communication failure with AI provider: " + e.getMessage(), model);
        }
    }
}
