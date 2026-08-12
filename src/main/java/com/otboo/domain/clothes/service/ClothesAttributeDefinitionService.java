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
import com.otboo.domain.clothes.exception.InvalidSortConditionException;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "name");
    private static final Set<String> ALLOWED_SORT_DIRECTIONS = Set.of("ASCENDING", "DESCENDING");

    public List<ClothesAttributeDefinitionResponse> getList(String sortBy, String sortDirection, String keywordLike) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortConditionException("sortBy", sortBy);
        }
        Sort.Direction direction = "DESCENDING".equals(sortDirection)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy);

        if (!ALLOWED_SORT_DIRECTIONS.contains(sortDirection)) {
            throw new InvalidSortConditionException("sortDirection", sortDirection);
        }
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
                .orElseGet(() -> saveNewDefinition(name));

        List<AttributeSelectableValue> selectableValues =
                syncSelectableValues(definition, values);

        userRepository.findAll().stream()
            .map(User::getId)
            .forEach(userId ->
                eventPublisher.publishEvent(
                    new NotificationEvent(
                        userId,
                        "새 의상 속성이 추가되었습니다.",
                        "'" + definition.getName() + "' 의상 속성이 추가되었습니다.",
                        NotificationLevel.INFO
                    )
                )
            );

        return mapper.toResponse(definition, selectableValues);
    }

    private ClothesAttributeDefinition saveNewDefinition(String name) {
        try {
            return definitionRepository.saveAndFlush(new ClothesAttributeDefinition(name));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateAttributeDefinitionNameException(name);
        }
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
        try {
            definitionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateAttributeDefinitionNameException(newName);
        }

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
