package com.otboo.domain.clothes.service;

import com.otboo.domain.clothes.dto.request.ClothesAttributeRequest;
import com.otboo.domain.clothes.dto.request.ClothesCreateRequest;
import com.otboo.domain.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.domain.clothes.dto.response.ClothesListResponse;
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
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
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
    public ClothesResponse create(UUID currentUserId, ClothesCreateRequest request) {
        if (!request.ownerId().equals(currentUserId)) {
            throw new AccessDeniedException("본인 명의로만 의상을 등록할 수 있습니다.");
        }

        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new UserNotFoundException(request.ownerId()));

        Clothes clothes = clothesRepository.save(
                new Clothes(owner, request.name().trim(), null, request.type())
        );

        List<ClothesAttributeRequest> attributeRequests =
                request.attributes() == null ? List.of() : request.attributes();

        Map<UUID, ClothesAttributeDefinition> definitionById = resolveDefinitions(attributeRequests);
        Map<UUID, List<String>> activeValuesByDefinitionId = resolveActiveValues(definitionById.values());
        List<ClothesAttribute> attributes = saveAttributes(clothes, attributeRequests, definitionById,
                activeValuesByDefinitionId);

        return clothesMapper.toResponse(clothes, attributes, activeValuesByDefinitionId);
    }

    @Transactional
    public ClothesResponse update(UUID currentUserId, UUID clothesId, ClothesUpdateRequest request) {
        Clothes clothes = clothesRepository.findById(clothesId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new ClothesNotFoundException(clothesId));

        if (!clothes.getOwner().getId().equals(currentUserId)) {
            throw new AccessDeniedException("본인 의상만 수정할 수 있습니다.");
        }

        clothes.update(request.name().trim(), request.type());
        clothesAttributeRepository.deleteByClothes(clothes);

        List<ClothesAttributeRequest> attributeRequests =
                request.attributes() == null ? List.of() : request.attributes();

        Map<UUID, ClothesAttributeDefinition> definitionById =
                resolveDefinitions(attributeRequests);
        Map<UUID, List<String>> activeValuesByDefinitionId =
                resolveActiveValues(definitionById.values());
        List<ClothesAttribute> attributes =
                saveAttributes(clothes, attributeRequests, definitionById, activeValuesByDefinitionId);

        return clothesMapper.toResponse(clothes, attributes, activeValuesByDefinitionId);
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

    @Transactional(readOnly = true)
    public ClothesListResponse getList(
            UUID currentUserId,
            UUID ownerId,
            ClothesType typeEqual,
            String cursor,
            UUID idAfter,
            int limit
    ) {
        if (!ownerId.equals(currentUserId)) {
            throw new AccessDeniedException("본인 옷장만 조회할 수 있습니다.");
        }

        Instant cursorInstant = parseCursor(cursor, idAfter);

        List<Clothes> clothesList = clothesRepository.findClothesList(
                ownerId, typeEqual, cursorInstant, idAfter, limit + 1
        );

        boolean hasNext = clothesList.size() > limit;
        if (hasNext) {
            clothesList = clothesList.subList(0, limit);
        }

        List<ClothesAttribute> attributes =
                clothesAttributeRepository.findByClothesIn(clothesList);
        Map<UUID, List<ClothesAttribute>> attributesByClothesId =
                attributes.stream()
                        .collect(Collectors.groupingBy(attribute ->
                                attribute.getClothes().getId()));

        List<ClothesAttributeDefinition> definitions = attributes.stream()
                .map(ClothesAttribute::getDefinition)
                .distinct()
                .toList();
        Map<UUID, List<String>> selectableValuesByDefinitionId =
                resolveActiveValues(definitions);

        List<ClothesResponse> data = clothesList.stream()
                .map(item -> clothesMapper.toResponse(item,
                        attributesByClothesId.getOrDefault(item.getId(),
                                List.of()), selectableValuesByDefinitionId
                ))
                .toList();

        String nextCursor = null;
        UUID nextIdAfter = null;
        if (hasNext) {
            Clothes last = clothesList.get(clothesList.size() - 1);
            nextCursor = last.getCreatedAt().toString();
            nextIdAfter = last.getId();
        }

        long totalCount = clothesRepository.countClothes(ownerId, typeEqual);

        return new ClothesListResponse(
                data, nextCursor, nextIdAfter, hasNext, totalCount, "createdAt",
                "DESCENDING");
    }

    private Instant parseCursor(String cursor, UUID idAfter) {
        boolean hasCursor = cursor != null && !cursor.isBlank();
        boolean hasIdAfter = idAfter != null;

        if (hasCursor != hasIdAfter) {
            throw new InvalidClothesCursorException();
        }

        if (!hasCursor) {
            return null;
        }

        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException e) {
            throw new InvalidClothesCursorException();
        }
    }
}
