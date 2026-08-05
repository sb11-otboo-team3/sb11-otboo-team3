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

    private Optional<Clothes> pickBest(
            List<Clothes> candidates,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity
    ) {
        return candidates.stream()
                .max(Comparator
                        .comparingDouble((Clothes clothes) -> scoreCalculator.calculateScore(
                                clothes.getType(), minTemperature, maxTemperature, precipitationType, temperatureSensitivity
                        ))
                        .thenComparing(Clothes::getCreatedAt));
    }
}
