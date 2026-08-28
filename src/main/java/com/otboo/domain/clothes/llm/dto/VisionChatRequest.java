package com.otboo.domain.clothes.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record VisionChatRequest(
        String model,
        List<VisionMessage> messages,
        @JsonProperty("response_format") OpenRouterResponseFormat responseFormat
) {
}
