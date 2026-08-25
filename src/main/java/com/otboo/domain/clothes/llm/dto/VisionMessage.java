package com.otboo.domain.clothes.llm.dto;

import java.util.List;

public record VisionMessage(String role, List<VisionContentPart> content) {
    public static VisionMessage user(String text, String imageDataUrl) {
        return new VisionMessage("user", List.of(VisionContentPart.text(text), VisionContentPart.image(imageDataUrl)));
    }
}
