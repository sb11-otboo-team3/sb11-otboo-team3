package com.otboo.domain.recommendation.service;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.repository.ClothesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationCandidateService {

    private final ClothesRepository clothesRepository;

    public Map<ClothesType, List<Clothes>> getCandidatesByType(UUID ownerId){
        List<Clothes> candidates = clothesRepository.findByOwner_IdAndDeletedAtIsNull(ownerId);
        return candidates.stream()
                .collect(Collectors.groupingBy(Clothes::getType));
    }

    public List<Clothes> getByIds(List<UUID> clothesIds) {
        return clothesRepository.findByIdInAndDeletedAtIsNull(clothesIds);
    }
}
