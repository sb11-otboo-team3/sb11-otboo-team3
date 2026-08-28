package com.otboo.domain.recommendation.llm.dto;

import java.util.List;

public record OpenRouterChatResponse(List<Choice> choices) {

    public record Choice(OpenRouterMessage message) {
    }
}
