package com.otboo.domain.clothes.dto.response;

import com.otboo.domain.clothes.entity.ClothesType;

import java.util.List;
import java.util.UUID;

public record ClothesResponse(
        UUID id,
        UUID ownerId,
        String name,
        String imageUrl,
        ClothesType type,
        List<ClothesAttributeResponse> attributes
) {
}
