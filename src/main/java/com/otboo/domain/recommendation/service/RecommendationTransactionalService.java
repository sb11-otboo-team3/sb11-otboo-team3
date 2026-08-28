package com.otboo.domain.recommendation.service;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.mapper.ClothesMapper;
import
        com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.recommendation.dto.response.RecommendationClothesResponse;
import com.otboo.domain.recommendation.llm.dto.RankedOutfit;
import com.otboo.domain.weather.entity.PrecipitationType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationTransactionalService {

    private final OutfitRecommendationEngine recommendationEngine;
    private final ClothesAttributeRepository clothesAttributeRepository;
    private final AttributeSelectableValueRepository selectableValueRepository;
    private final ClothesMapper clothesMapper;

    @Transactional(readOnly = true)
    public List<RecommendationClothesResponse> recommend(
            UUID ownerId,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity,
            Optional<List<RankedOutfit>> rankedOutfits
    ) {
        List<Clothes> combination = resolveCombination(
                ownerId, minTemperature, maxTemperature, precipitationType, temperatureSensitivity, rankedOutfits
        );

        List<ClothesAttribute> attributes = clothesAttributeRepository.findByClothesIn(combination);
        Map<UUID, List<ClothesAttribute>> attributesByClothesId =
                attributes.stream()
                        .collect(Collectors.groupingBy(attribute ->
                                attribute.getClothes().getId()));

        List<ClothesAttributeDefinition> definitions =
                attributes.stream()
                        .map(ClothesAttribute::getDefinition)
                        .distinct()
                        .toList();
        Map<UUID, List<String>> selectableValuesByDefinitionId = selectableValueRepository
                .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(definitions)
                .stream()
                .collect(Collectors.groupingBy(
                        value -> value.getDefinition().getId(),
                        Collectors.mapping(AttributeSelectableValue::getValue,
                                Collectors.toList())
                ));
        return combination.stream()
                .map(clothes -> clothesMapper.toResponse(
                        clothes, attributesByClothesId.getOrDefault(clothes.getId(), List.of()), selectableValuesByDefinitionId
                ))
                .map(this::toRecommendationClothesResponse)
                .toList();
    }

    private List<Clothes> resolveCombination(
            UUID ownerId,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity,
            Optional<List<RankedOutfit>> rankedOutfits
    ) {
        return rankedOutfits
                .filter(outfits -> !outfits.isEmpty())
                .map(this::pickRandomOutfit)
                .map(this::resolveIfComplete)
                .filter(clothes -> !clothes.isEmpty())
                .orElseGet(() -> recommendationEngine.recommend(
                        ownerId, minTemperature, maxTemperature, precipitationType, temperatureSensitivity));
    }

    // 같은 시간대엔 캐시된 랭킹 리스트가 항상 동일하므로, 재호출("다른 옷 추천")마다 다른 조합을 주기 위해 매번 무작위로 하나를 고른다.
    private RankedOutfit pickRandomOutfit(List<RankedOutfit> outfits) {
        return outfits.get(ThreadLocalRandom.current().nextInt(outfits.size()));
    }

    // 캐시 유지 중(TTL 이내) 조합 속 옷 일부가 삭제됐으면 resolveFromRanked()가 일부만 반환한다 -
    // 개수가 안 맞는 불완전한 조합을 그대로 내보내지 않고, 빈 리스트로 만들어 폴백을 타게 한다.
    private List<Clothes> resolveIfComplete(RankedOutfit outfit) {
        List<Clothes> resolved = recommendationEngine.resolveFromRanked(outfit.clothesIds());
        return resolved.size() == outfit.clothesIds().size() ? resolved : List.of();
    }

    private RecommendationClothesResponse toRecommendationClothesResponse(ClothesResponse response) {
        return new RecommendationClothesResponse(
                response.id(),
                response.name(),
                response.imageUrl(),
                response.type(),
                response.attributes()
        );
    }
}
