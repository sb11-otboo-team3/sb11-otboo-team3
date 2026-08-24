package com.otboo.domain.recommendation.llm.cache;

import com.otboo.domain.recommendation.llm.dto.RankedOutfit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationLlmCache {

    Optional<List<RankedOutfit>> find(UUID userId, Instant forecastAt);

    void save(UUID userId, Instant forecastAt, List<RankedOutfit> rankedOutfits);
}
