package com.otboo.domain.clothes.service;

import com.otboo.domain.clothes.dto.request.ClothesAttributeRequest;
import com.otboo.domain.clothes.dto.request.ClothesCreateRequest;
import com.otboo.domain.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.*;
import com.otboo.domain.clothes.exception.*;
import com.otboo.domain.clothes.mapper.ClothesMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.exception.UserNotFoundException;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.infrastructure.storage.event.FileReplacementEvent;
import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// DB 저장 전용 트랜잭션 빈 - 이미지 업로드(외부 블로킹 호출)와 트랜잭션 경계를 분리하기 위해
// ProfileUpdateTransactionalService/RecommendationTransactionalService와 동일한 패턴을 사용한다.
@Service
@RequiredArgsConstructor
public class ClothesWriteTransactionalService {

    private final ClothesRepository clothesRepository;
    private final ClothesAttributeRepository clothesAttributeRepository;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final AttributeSelectableValueRepository selectableValueRepository;
    private final UserRepository userRepository;
    private final ClothesMapper clothesMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ClothesResponse create(ClothesCreateRequest request, String imageKey) {
        if (imageKey != null) {
            eventPublisher.publishEvent(new FileReplacementEvent(null, imageKey));
        }

        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new UserNotFoundException(request.ownerId()));

        Clothes clothes = clothesRepository.save(
                new Clothes(owner, request.name().trim(), imageKey, request.type())
        );

        List<ClothesAttributeRequest> attributeRequests =
                request.attributes() == null ? List.of() : request.attributes();

        validateRequiredAttributes(attributeRequests);

        Map<UUID, ClothesAttributeDefinition> definitionById = resolveDefinitions(attributeRequests);
        Map<UUID, List<String>> activeValuesByDefinitionId = resolveActiveValues(definitionById.values());
        List<ClothesAttribute> attributes = saveAttributes(clothes, attributeRequests, definitionById,
                activeValuesByDefinitionId);

        return clothesMapper.toResponse(clothes, attributes, activeValuesByDefinitionId);
    }

    @Transactional
    public ClothesResponse update(UUID currentUserId, UUID clothesId, ClothesUpdateRequest request, String newImageKey) {
        Clothes clothes = clothesRepository.findById(clothesId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new ClothesNotFoundException(clothesId));

        if (!clothes.getOwner().getId().equals(currentUserId)) {
            throw new AccessDeniedException("본인 의상만 수정할 수 있습니다.");
        }
        
        String oldImageKey = clothes.getImageKey();
        if (newImageKey != null) {
            clothes.updateImageKey(newImageKey);
            eventPublisher.publishEvent(new FileReplacementEvent(oldImageKey, newImageKey));
        } else if (Boolean.TRUE.equals(request.deleteImage())) {
            clothes.updateImageKey(null);
            eventPublisher.publishEvent(new FileReplacementEvent(oldImageKey, null));
            
        }

        clothes.update(request.name().trim(), request.type());
        clothesAttributeRepository.deleteByClothes(clothes);
        // Hibernate는 기본적으로 INSERT를 DELETE보다 먼저 flush하므로, 삭제를 즉시 반영해두지 않으면
        // 기존과 동일한 (clothes_id, definition_id) 조합을 다시 저장할 때 유니크 제약 위반이 발생한다.
        clothesAttributeRepository.flush();


        List<ClothesAttributeRequest> attributeRequests =
                request.attributes() == null ? List.of() : request.attributes();

        validateRequiredAttributes(attributeRequests);

        Map<UUID, ClothesAttributeDefinition> definitionById =
                resolveDefinitions(attributeRequests);
        Map<UUID, List<String>> activeValuesByDefinitionId =
                resolveActiveValues(definitionById.values());
        List<ClothesAttribute> attributes =
                saveAttributes(clothes, attributeRequests, definitionById, activeValuesByDefinitionId);

        return clothesMapper.toResponse(clothes, attributes, activeValuesByDefinitionId);
    }

    private void validateRequiredAttributes(List<ClothesAttributeRequest> attributeRequests) {
        List<ClothesAttributeDefinition> requiredDefinitions =
                definitionRepository.findByDeletedAtIsNullAndRequiredTrue();
        if (requiredDefinitions.isEmpty()) {
            return;
        }

        Set<UUID> providedDefinitionIds = attributeRequests.stream()
                .map(ClothesAttributeRequest::definitionId)
                .collect(Collectors.toSet());

        List<String> missingNames = requiredDefinitions.stream()
                .filter(definition -> !providedDefinitionIds.contains(definition.getId()))
                .map(ClothesAttributeDefinition::getName)
                .toList();

        if (!missingNames.isEmpty()) {
            throw new RequiredClothesAttributeMissingException(missingNames);
        }
    }

    private Map<UUID, ClothesAttributeDefinition> resolveDefinitions(
            List<ClothesAttributeRequest> attributeRequests) {
        List<UUID> definitionIds = attributeRequests.stream()
                .map(ClothesAttributeRequest::definitionId)
                .toList();

        if (definitionIds.size() != Set.copyOf(definitionIds).size()) {
            throw new DuplicateClothesAttributeException();
        }

        return definitionRepository.findAllById(definitionIds).stream()
                .filter(definition -> definition.getDeletedAt() == null)
                .collect(Collectors.toMap(ClothesAttributeDefinition::getId, Function.identity()));
    }

    private Map<UUID, List<String>> resolveActiveValues(Collection<ClothesAttributeDefinition> definitions) {
        return selectableValueRepository
                .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.copyOf(definitions))
                .stream()
                .collect(Collectors.groupingBy(
                        value -> value.getDefinition().getId(),
                        Collectors.mapping(AttributeSelectableValue::getValue, Collectors.toList())
                ));
    }

    private List<ClothesAttribute> saveAttributes(
            Clothes clothes,
            List<ClothesAttributeRequest> attributeRequests,
            Map<UUID, ClothesAttributeDefinition> definitionsById,
            Map<UUID, List<String>> activeValuesByDefinitionId
    ) {
        List<ClothesAttribute> attributes = new ArrayList<>();
        for (ClothesAttributeRequest attributeRequest : attributeRequests) {
            ClothesAttributeDefinition definition = definitionsById.get(attributeRequest.definitionId());
            if (definition == null) {
                throw new ClothesAttributeDefinitionNotFoundException(attributeRequest.definitionId());
            }

            String value = attributeRequest.value().trim();
            if (!activeValuesByDefinitionId.getOrDefault(definition.getId(), List.of()).contains(value)) {
                throw new InvalidClothesAttributeValueException(definition.getId(), value);
            }
            attributes.add(clothesAttributeRepository.save(new ClothesAttribute(clothes, definition, value)));
        }

        return attributes;
    }
}