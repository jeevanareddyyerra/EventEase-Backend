package com.eventease.controller;

import com.eventease.dto.request.AiChatRequest;
import com.eventease.dto.response.AiChatResponse;
import com.eventease.dto.response.AiCitationResponse;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.exception.BusinessRuleException;
import com.eventease.rag.assistant.AiAssistantService;
import com.eventease.rag.assistant.GroundedAnswerResponse;
import com.eventease.service.EventService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller exposing RAG-grounded AI Assistant chat operations.
 * Requires authenticated access and enforces public event visibility boundaries.
 */
@RestController
@RequestMapping("/api/ai/assistant")
public class AiAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    private final AiAssistantService aiAssistantService;
    private final EventService eventService;

    public AiAssistantController(AiAssistantService aiAssistantService, EventService eventService) {
        this.aiAssistantService = aiAssistantService;
        this.eventService = eventService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        log.debug("Received chat request: questionLength={}, eventId={}, documentType={}",
                request.getQuestion() != null ? request.getQuestion().length() : 0,
                request.getEventId(),
                request.getDocumentType());

        // Enforce event existence and public visibility if eventId is supplied
        if (request.getEventId() != null) {
            Event event = eventService.getEventById(request.getEventId());
            if (event.getStatus() != EventStatus.PUBLISHED) {
                log.warn("Attempt to ask question about non-published event #{} with status {}",
                        request.getEventId(), event.getStatus());
                throw new BusinessRuleException(
                        "Questions can only be asked for PUBLISHED events. Current event status: " + event.getStatus(),
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        GroundedAnswerResponse answerResponse = aiAssistantService.answerQuestion(
                request.getQuestion(),
                request.getEventId(),
                request.getDocumentType()
        );

        return ResponseEntity.ok(toResponse(answerResponse));
    }

    private AiChatResponse toResponse(GroundedAnswerResponse result) {
        List<AiCitationResponse> citations = result.getSources() != null
                ? result.getSources().stream()
                        .map(s -> new AiCitationResponse(
                                s.getEventId(),
                                s.getEventTitle(),
                                s.getDocumentId(),
                                s.getDocumentTitle(),
                                s.getDocumentType(),
                                s.getChunkId(),
                                s.getSimilarityScore()
                        ))
                        .collect(Collectors.toList())
                : Collections.emptyList();

        return new AiChatResponse(
                result.getAnswer(),
                result.isGrounded(),
                result.isEvidenceFound(),
                citations,
                result.getTopSimilarityScore(),
                result.getModelUsed(),
                result.getFallbackReason()
        );
    }
}
