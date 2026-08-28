package com.otboo.domain.recommendation.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenRouterChatRequest(
        String model,
        List<OpenRouterMessage> messages,
        @JsonProperty("response_format") OpenRouterResponseFormat responseFormat
) {
}
