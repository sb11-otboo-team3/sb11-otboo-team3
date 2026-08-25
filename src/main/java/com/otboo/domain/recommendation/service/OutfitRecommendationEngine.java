package com.otboo.domain.recommendation.service;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.recommendation.llm.dto.OutfitCandidate;
import com.otboo.domain.weather.entity.PrecipitationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class OutfitRecommendationEngine {

    private static final Set<ClothesType> COMBINATION_TYPES = Set.of(
            ClothesType.TOP, ClothesType.BOTTOM, ClothesType.DRESS, ClothesType.OUTER, ClothesType.SHOES);

    private static final double MINIMUM_INCLUSION_SCORE = 0.0;
    private static final int TOP_CANDIDATE_COUNT = 3;

    private final RecommendationCandidateService candidateService;
    private final OutfitCombinationRule combinationRule;
    private final ClothesScoreCalculator scoreCalculator;
    private final ClothesAttributeRepository clothesAttributeRepository;

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

    @Transactional(readOnly = true)
    public List<OutfitCandidate> buildCandidates(
            UUID ownerId,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity
    ) {
        Map<ClothesType, List<Clothes>> candidatedByType =
                combinationRule.apply(candidateService.getCandidatesByType(ownerId));

        List<Clothes> filtered = candidatedByType.entrySet().stream()
                .filter(entry -> COMBINATION_TYPES.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .filter(clothes -> scoreCalculator.calculateScore(
                        clothes.getType(), minTemperature, maxTemperature, precipitationType, temperatureSensitivity
                ) > MINIMUM_INCLUSION_SCORE)
                .toList();

        if (filtered.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ClothesAttribute>> attributesByClothesId = clothesAttributeRepository.findByClothesIn(filtered)
                .stream()
                .collect(Collectors.groupingBy(attribute -> attribute.getClothes().getId()));

        return filtered.stream()
                .map(clothes -> toOutfitCandidate(clothes, attributesByClothesId.getOrDefault(clothes.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Clothes> resolveFromRanked(List<UUID> clothesIds) {
        return candidateService.getByIds(clothesIds);
    }

    private OutfitCandidate toOutfitCandidate(Clothes clothes, List<ClothesAttribute> attributes) {
        List<String> attributeTexts = attributes.stream()
                .map(attribute -> attribute.getDefinition().getName() + ": " + attribute.getValue())
                .toList();
        return new OutfitCandidate(clothes.getId(), clothes.getType(), clothes.getName(), attributeTexts);
    }

    private Optional<Clothes> pickBest(
            List<Clothes> candidates,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity
    ) {
        List<ScoredClothes> scored = new ArrayList<>(candidates.stream()
                .map(clothes -> new ScoredClothes(
                        clothes,
                        scoreCalculator.calculateScore(
                                clothes.getType(), minTemperature, maxTemperature, precipitationType, temperatureSensitivity
                        )
                ))
                .filter(s -> s.score() > MINIMUM_INCLUSION_SCORE)
                .toList());

        // 동점 후보가 항상 같은 순서로 잘리지 않도록, 점수 정렬 전에 먼저 섞는다.
        Collections.shuffle(scored);

        List<Clothes> topCandidates = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredClothes::score).reversed())
                .map(ScoredClothes::clothes)
                .limit(TOP_CANDIDATE_COUNT)
                .toList();

        if (topCandidates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(topCandidates.get(ThreadLocalRandom.current().nextInt(topCandidates.size())));
    }

    private record ScoredClothes(Clothes clothes, double score) {
    }
}
