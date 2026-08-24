package com.otboo.domain.recommendation.llm.dto;

public record OpenRouterResponseFormat(String type) {
    public static OpenRouterResponseFormat jsonObject() {
        return new OpenRouterResponseFormat("json_object");
    }
}
