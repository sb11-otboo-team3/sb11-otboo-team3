package com.otboo.domain.recommendation.dto.response;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(
        UUID weatherId,
        UUID userId,
        List<RecommendationClothesResponse> clothes
) {
}
