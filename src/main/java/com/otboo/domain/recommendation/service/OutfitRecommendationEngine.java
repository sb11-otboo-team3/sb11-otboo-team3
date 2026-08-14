package com.otboo.domain.recommendation.service;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.weather.entity.PrecipitationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class OutfitRecommendationEngine {

    private static final Set<ClothesType> COMBINATION_TYPES = Set.of(
            ClothesType.TOP, ClothesType.BOTTOM, ClothesType.DRESS, ClothesType.OUTER, ClothesType.SHOES);

    private static final double MINIMUM_INCLUSION_SCORE = 0.0;

    private final RecommendationCandidateService candidateService;
    private final OutfitCombinationRule combinationRule;
    private final ClothesScoreCalculator scoreCalculator;

    public List<Clothes> recommend(
            UUID ownerId,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity,
            Set<UUID> excludeClothesIds
    ) {
        Map<ClothesType, List<Clothes>> candidatedByType =
                combinationRule.apply(candidateService.getCandidatesByType(ownerId));

        List<Clothes> combination = new ArrayList<>();
        for (Map.Entry<ClothesType, List<Clothes>> entry: candidatedByType.entrySet()) {
            if (!COMBINATION_TYPES.contains(entry.getKey())) {
                continue;
            }

            List<Clothes> candidates = entry.getValue().stream()
                            .filter(clothes -> !excludeClothesIds.contains(clothes.getId()))
                                    .toList();

            pickBest(candidates, minTemperature, maxTemperature, precipitationType, temperatureSensitivity)
                    .ifPresent(combination::add);
        }

        return combination;
    }

    private Optional<Clothes> pickBest(
            List<Clothes> candidates,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity
    ) {
        return candidates.stream()
                .map(clothes -> new ScoredClothes(
                        clothes,
                        scoreCalculator.calculateScore(
                                clothes.getType(), minTemperature, maxTemperature, precipitationType, temperatureSensitivity
                        )
                ))
                .filter(scored -> scored.score() > MINIMUM_INCLUSION_SCORE)
                .max(Comparator
                        .comparingDouble(ScoredClothes::score)
                        .thenComparing(scored -> scored.clothes().getCreatedAt()))
                .map(ScoredClothes::clothes);
    }

    private record ScoredClothes(Clothes clothes, double score) {
    }
}
