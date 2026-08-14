package com.otboo.domain.recommendation.dto.response;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(
        UUID weatherId,
        List<RecommendationClothesResponse> clothes
) {
}
