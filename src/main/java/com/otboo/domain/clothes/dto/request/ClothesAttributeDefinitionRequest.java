package com.otboo.domain.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ClothesAttributeDefinitionRequest(
        @NotBlank
        String name,
        List<@NotBlank String> selectableValues
) {
}
