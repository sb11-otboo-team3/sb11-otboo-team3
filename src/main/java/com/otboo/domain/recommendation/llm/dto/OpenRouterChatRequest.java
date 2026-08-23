package com.otboo.domain.recommendation.llm.dto;

import java.util.List;

public record OpenRouterChatRequest(String model, List<OpenRouterMessage> messages) {
}
