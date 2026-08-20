package com.otboo.domain.clothes.extraction.mapper;

import com.otboo.domain.clothes.dto.response.ClothesAttributeResponse;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.extraction.dto.RawProductAttribute;
import com.otboo.domain.clothes.extraction.dto.RawProductInfo;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ClothesExtractionMapper {

    private static final Map<ClothesType, List<String>> TYPE_KEYWORDS = Map.ofEntries(
            Map.entry(ClothesType.TOP, List.of("상의", "티셔츠", "셔츠", "니트", "블라우스", "맨투맨", "후드", "top_")),
            Map.entry(ClothesType.BOTTOM, List.of("하의", "바지", "팬츠", "스커트", "청바지", "슬랙스", "bottom_")),
            Map.entry(ClothesType.OUTER, List.of("아우터", "자켓", "재킷", "코트", "패딩", "점퍼", "outer_")),
            Map.entry(ClothesType.DRESS, List.of("원피스", "드레스")),
            Map.entry(ClothesType.SHOES, List.of("신발", "슈즈", "운동화", "스니커즈", "shoes_")),
            Map.entry(ClothesType.SOCKS, List.of("양말", "삭스")),
            Map.entry(ClothesType.HAT, List.of("모자", "캡", "비니")),
            Map.entry(ClothesType.BAG, List.of("가방", "백")),
            Map.entry(ClothesType.SCARF, List.of("스카프", "머플러")),
            Map.entry(ClothesType.UNDERWEAR, List.of("속옷", "언더웨어")),
            Map.entry(ClothesType.ACCESSORY, List.of("액세서리", "주얼리"))
    );

    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final AttributeSelectableValueRepository selectableValueRepository;

    public ClothesResponse toClothesResponse(RawProductInfo raw, UUID ownerId) {
        ClothesType type = resolveType(raw.categoryHints());
        List<ClothesAttributeResponse> attributes = resolveAttributes(raw.attributes());

        return new ClothesResponse(null, ownerId, raw.name(), raw.imageUrl(), type, attributes);
    }

    private ClothesType resolveType(List<String> categoryHints) {
        for (String hint : categoryHints) {
            for (Map.Entry<ClothesType, List<String>> entry : TYPE_KEYWORDS.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (hint.contains(keyword)) {
                        return entry.getKey();
                    }
                }
            }
        }
        return ClothesType.ETC;
    }

    private List<ClothesAttributeResponse> resolveAttributes(List<RawProductAttribute> rawAttributes) {
        List<ClothesAttributeResponse> result = new ArrayList<>();

        for (RawProductAttribute raw : rawAttributes) {
            definitionRepository.findByName(raw.name())
                    .filter(definition -> definition.getDeletedAt() == null)
                    .ifPresent(definition -> resolveMatchedValue(definition, raw.values())
                            .ifPresent(matched -> result.add(new ClothesAttributeResponse(
                                    definition.getId(), definition.getName(), matched.activeValues(), matched.value()
                            ))));
        }

        return result;
    }

    private Optional<MatchedValue> resolveMatchedValue(ClothesAttributeDefinition definition, List<String> rawValues) {
        List<String> activeValues = selectableValueRepository
                .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition))
                .stream()
                .map(AttributeSelectableValue::getValue)
                .toList();

        return rawValues.stream()
                .filter(activeValues::contains)
                .findFirst()
                .map(value -> new MatchedValue(value, activeValues));
    }

    private record MatchedValue(String value, List<String> activeValues) {
    }
}
