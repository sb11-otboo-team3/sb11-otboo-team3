package com.otboo.domain.clothes.llm.dto;

import java.util.List;

public record OpenRouterChatResponse(List<Choice> choices) {

    public record Choice(Message message) {
    }

    public record Message(String role, String content) {
    }
}
