package com.otboo.domain.clothes.service;

import com.otboo.domain.clothes.dto.request.ClothesAttributeRequest;
import com.otboo.domain.clothes.dto.request.ClothesCreateRequest;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import
        com.otboo.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.domain.clothes.exception.DuplicateClothesAttributeException;
import com.otboo.domain.clothes.exception.InvalidClothesAttributeValueException;
import com.otboo.domain.clothes.mapper.ClothesMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.exception.UserNotFoundException;
import com.otboo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClothesService {
    private final ClothesRepository clothesRepository;
    private final ClothesAttributeRepository clothesAttributeRepository;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final AttributeSelectableValueRepository selectableValueRepository;
    private final UserRepository userRepository;
    private final ClothesMapper clothesMapper;

    @Transactional
    public ClothesResponse create(UUID currentUserId, ClothesCreateRequest
            request) {
        if (!request.ownerId().equals(currentUserId)) {
            throw new AccessDeniedException("본인 명의로만 의상을 등록할 수 있습니다.");
        }

        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new
                        UserNotFoundException(request.ownerId()));

        Clothes clothes = clothesRepository.save(
                new Clothes(owner, request.name().trim(), null, request.type())
        );

        List<ClothesAttributeRequest> attributeRequests =
                request.attributes() == null ? List.of() : request.attributes();
        List<UUID> definitionIds = attributeRequests.stream()
                .map(ClothesAttributeRequest::definitionId)
                .toList();

        if (definitionIds.size() != Set.copyOf(definitionIds).size()) {
            throw new DuplicateClothesAttributeException();
        }

        Map<UUID, ClothesAttributeDefinition> definitionsById =
                definitionRepository.findAllById(definitionIds)
                        .stream()
                        .filter(definition -> definition.getDeletedAt() == null)
                        .collect(Collectors.toMap(ClothesAttributeDefinition::getId,
                                Function.identity()));

        Map<UUID, List<String>> activeValuesByDefinitionId =
                selectableValueRepository
                        .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List
                                .copyOf(definitionsById.values()))
                        .stream()
                        .collect(Collectors.groupingBy(
                                value -> value.getDefinition().getId(),
                                Collectors.mapping(AttributeSelectableValue::getValue,
                                        Collectors.toList())
                        ));

        List<ClothesAttribute> attributes = new ArrayList<>();
        for (ClothesAttributeRequest attributeRequest : attributeRequests) {
            ClothesAttributeDefinition definition =
                    definitionsById.get(attributeRequest.definitionId());
            if (definition == null) {
                throw new
                        ClothesAttributeDefinitionNotFoundException(attributeRequest.definitionId());
            }

            String value = attributeRequest.value().trim();
            if (!activeValuesByDefinitionId.getOrDefault(definition.getId(),
                    List.of()).contains(value)) {
                throw new
                        InvalidClothesAttributeValueException(definition.getId(), value);
            }

            attributes.add(clothesAttributeRepository.save(new
                    ClothesAttribute(clothes, definition, value)));
        }

        return clothesMapper.toResponse(clothes, attributes,
                activeValuesByDefinitionId);
    }
}