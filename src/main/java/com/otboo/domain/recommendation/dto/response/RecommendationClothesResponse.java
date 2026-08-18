package com.otboo.domain.recommendation.dto.response;

import com.otboo.domain.clothes.dto.response.ClothesAttributeResponse;
import com.otboo.domain.clothes.entity.ClothesType;

import java.util.List;
import java.util.UUID;

public record RecommendationClothesResponse(
        UUID clothesId,
        String name,
        String imageUrl,
        ClothesType type,
        List<ClothesAttributeResponse> attributes
) {
}
