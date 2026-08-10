package com.otboo.domain.recommendation.service;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class OutfitCombinationRule {

    public Map<ClothesType, List<Clothes>> apply(Map<ClothesType, List<Clothes>> candidatesByType) {
        if (!candidatesByType.getOrDefault(ClothesType.DRESS, List.of()).isEmpty()) {
            Map<ClothesType, List<Clothes>> adjusted = new EnumMap<>(candidatesByType);
            adjusted.remove(ClothesType.TOP);
            adjusted.remove(ClothesType.BOTTOM);
            return adjusted;
        }

        return candidatesByType;
    }

}
