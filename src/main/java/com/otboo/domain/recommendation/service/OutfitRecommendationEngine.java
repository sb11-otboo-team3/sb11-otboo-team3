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

    private final RecommendationCandidateService candidateService;
    private final OutfitCombinationRule combinationRule;
    private final ClothesScoreCalculator scoreCalculator;

    public List<Clothes> recommend(
            UUID ownerId,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity
    ) {
        Map<ClothesType, List<Clothes>> candidatedByType =
                combinationRule.apply(candidateService.getCandidatesByType(ownerId));

        List<Clothes> combination = new ArrayList<>();
        for (Map.Entry<ClothesType, List<Clothes>> entry: candidatedByType.entrySet()) {
            if (!COMBINATION_TYPES.contains(entry.getKey())) {
                continue;
            }

            pickBest(entry.getValue(), minTemperature, maxTemperature, precipitationType, temperatureSensitivity)
                    .ifPresent(combination::add);
        }

        return combination;
    }

    // 점수가 0이면 "안 입어도 되는" 상태(예: 더운 날의 아우터)라 조합에서 아예 제외한다.
    private static final double MINIMUM_INCLUSION_SCORE = 0.0;

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
