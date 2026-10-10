package com.eventease.rag.client;

/**
 * Encapsulates the response from an AI model completion invocation.
 */
public class AiModelResponse {

    private final String text;
    private final boolean successful;
    private final String errorMessage;
    private final String modelName;

    private AiModelResponse(String text, boolean successful, String errorMessage, String modelName) {
        this.text = text;
        this.successful = successful;
        this.errorMessage = errorMessage;
        this.modelName = modelName;
    }

    public static AiModelResponse success(String text, String modelName) {
        return new AiModelResponse(text, true, null, modelName);
    }

    public static AiModelResponse failure(String errorMessage, String modelName) {
        return new AiModelResponse(null, false, errorMessage, modelName);
    }

    public String getText() {
        return text;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public String toString() {
        return "AiModelResponse{" +
                "successful=" + successful +
                ", modelName='" + modelName + '\'' +
                ", hasText=" + (text != null && !text.isEmpty()) +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
