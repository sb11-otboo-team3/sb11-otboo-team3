package com.otboo.domain.clothes.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisionContentPart(String type, String text, ImageUrl imageUrl) {

    public record ImageUrl(String url) {
    }

    public static VisionContentPart text(String text) {
        return new VisionContentPart("text", text, null);
    }

    public static VisionContentPart image(String dataUrl) {
        return new VisionContentPart("image_url", null, new ImageUrl(dataUrl));
    }
}
