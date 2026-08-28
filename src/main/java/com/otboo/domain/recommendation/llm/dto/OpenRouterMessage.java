package com.otboo.domain.recommendation.llm.dto;

public record OpenRouterMessage(String role, String content) {

    public static OpenRouterMessage user(String content) {
        return new OpenRouterMessage("user", content);
    }
}
