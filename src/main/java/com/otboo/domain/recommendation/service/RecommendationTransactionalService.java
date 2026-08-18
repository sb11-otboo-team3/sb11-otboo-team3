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
import com.otboo.domain.weather.entity.PrecipitationType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
            Set<UUID> excludeClothesIds
    ) {
        List<Clothes> combination = recommendationEngine.recommend(
                ownerId, minTemperature, maxTemperature, precipitationType, temperatureSensitivity, excludeClothesIds
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
