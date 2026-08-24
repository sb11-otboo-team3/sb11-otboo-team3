package com.otboo.domain.recommendation.llm.dto;

import com.otboo.domain.clothes.entity.ClothesType;

import java.util.List;
import java.util.UUID;

public record OutfitCandidate(
        UUID id,
        ClothesType type,
        String name,
        List<String> attributes
) {
}
