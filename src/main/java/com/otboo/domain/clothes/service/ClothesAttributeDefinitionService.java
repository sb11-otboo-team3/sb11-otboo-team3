package com.otboo.domain.clothes.service;

import com.otboo.domain.clothes.dto.request.ClothesAttributeDefinitionRequest;
import com.otboo.domain.clothes.dto.response.ClothesAttributeDefinitionResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.domain.clothes.exception.DuplicateAttributeDefinitionNameException;
import com.otboo.domain.clothes.mapper.ClothesAttributeDefinitionMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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

    @Transactional
    public ClothesAttributeDefinitionResponse create(ClothesAttributeDefinitionRequest request) {
        String name = request.name().trim();
        List<String> values = normalizeValues(request.selectableValues());

        ClothesAttributeDefinition definition = definitionRepository.findByName(name)
                .map(existing -> {
                    if (existing.getDeletedAt() == null) {
                        throw new DuplicateAttributeDefinitionNameException(name);
                    }
                    existing.restore();
                    return existing;
                })
                .orElseGet(() -> definitionRepository.save(new ClothesAttributeDefinition(name)));

        List<AttributeSelectableValue> selectableValues =
                syncSelectableValues(definition, values);

        return mapper.toResponse(definition, selectableValues);
    }
    @Transactional
    public ClothesAttributeDefinitionResponse update(UUID definitionId, ClothesAttributeDefinitionRequest request) {
        ClothesAttributeDefinition definition = definitionRepository.findById(definitionId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new ClothesAttributeDefinitionNotFoundException(definitionId));

        String newName = request.name().trim();
        if (!newName.equals(definition.getName()) && definitionRepository.findByName(newName).isPresent()) {
            throw new DuplicateAttributeDefinitionNameException(newName);
        }
        definition.updateName(newName);

        List<String> values = normalizeValues(request.selectableValues());
        Set<String> newValueSet = new HashSet<>(values);

        List<AttributeSelectableValue> currentActiveValues =
                selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition));

        for (AttributeSelectableValue current : currentActiveValues) {
            if (!newValueSet.contains(current.getValue())) {
                current.delete();
            }
        }

        List<AttributeSelectableValue> selectableValues =
                syncSelectableValues(definition, values);

        return mapper.toResponse(definition, selectableValues);
    }

    private List<AttributeSelectableValue> syncSelectableValues(
            ClothesAttributeDefinition definition, List<String> values) {
        List<AttributeSelectableValue> selectableValues = new ArrayList<>();
        for (int i = 0; i< values.size(); i++) {
            String value = values.get(i);
            int displayOrder = i;
            AttributeSelectableValue selectableValue = selectableValueRepository
                    .findByDefinitionAndValue(definition, value)
                    .orElseGet(() -> selectableValueRepository.save(
                            new AttributeSelectableValue(definition, value, displayOrder)));
            selectableValue.restore();
            selectableValue.updateDisplayOrder(i);
            selectableValues.add(selectableValue);
        }
        return selectableValues;
    }

    @Transactional
    public void delete(UUID definitionId) {
        ClothesAttributeDefinition definition =
                definitionRepository.findById(definitionId)
                        .filter(found -> found.getDeletedAt() == null)
                        .orElseThrow(() -> new ClothesAttributeDefinitionNotFoundException(definitionId));

        List<AttributeSelectableValue> activeValues =
                selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition));
        activeValues.forEach(AttributeSelectableValue::delete);

        definition.delete();
    }


    private List<String> normalizeValues(List<String> rawValues) {
        if (rawValues == null) {
            return List.of();
        }
        return rawValues.stream()
                .map(String::trim)
                .distinct()
                .toList();
    }
}
