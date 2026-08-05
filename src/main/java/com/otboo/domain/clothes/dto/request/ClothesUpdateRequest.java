package com.otboo.domain.clothes.dto.request;

import com.otboo.domain.clothes.entity.ClothesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ClothesUpdateRequest(
        @NotBlank
        String name,
        @NotNull
        ClothesType type,
        @Valid
        List<ClothesAttributeRequest> attributes,
        Boolean deleteImage
) {
}
