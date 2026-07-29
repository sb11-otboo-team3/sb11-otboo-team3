package com.otboo.domain.clothes.service;

import com.otboo.domain.clothes.dto.response.ClothesAttributeDefinitionResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.mapper.ClothesAttributeDefinitionMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClothesAttributeDefinitionService {

    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final AttributeSelectableValueRepository selectableValueRepository;
    private final ClothesAttributeDefinitionMapper mapper;

    public List<ClothesAttributeDefinitionResponse> getList(String sortBy, String sortDirection, String keywordLike) {
        Sort.Direction direction = "DESCENDING".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy);
        String keyword = keywordLike == null ? "" : keywordLike;

        List<ClothesAttributeDefinition> definitions =
                definitionRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(keyword, sort);

        List<AttributeSelectableValue> selectableValues =
                selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(definitions);

        Map<UUID, List<AttributeSelectableValue>> valuesByDefinitionId = selectableValues.stream()
                .collect(Collectors.groupingBy(value ->
                        value.getDefinition().getId()));

        return definitions.stream()
                .map(definition -> mapper.toResponse(
                        definition, valuesByDefinitionId.getOrDefault(definition.getId(), List.of())
                ))
                .toList();
    }
}
