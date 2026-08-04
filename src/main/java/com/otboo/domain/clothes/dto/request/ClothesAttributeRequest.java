package com.otboo.domain.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClothesAttributeRequest(
        @NotNull
        UUID definitionId,
        @NotBlank
        String value
) {
}
