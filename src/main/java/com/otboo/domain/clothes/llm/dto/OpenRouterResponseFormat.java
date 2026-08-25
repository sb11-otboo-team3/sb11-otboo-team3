package com.otboo.domain.clothes.llm.dto;

public record OpenRouterResponseFormat(String type) {
    public static OpenRouterResponseFormat jsonObject() {
        return new OpenRouterResponseFormat("json_object");
    }
}
