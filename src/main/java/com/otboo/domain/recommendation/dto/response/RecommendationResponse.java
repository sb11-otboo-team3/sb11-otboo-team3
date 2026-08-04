package com.otboo.domain.recommendation.dto.response;

import com.otboo.domain.clothes.dto.response.ClothesResponse;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(
        UUID weatherId,
        List<ClothesResponse> clothes
){
}
