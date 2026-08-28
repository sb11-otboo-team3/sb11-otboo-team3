package com.otboo.domain.clothes.service;

import com.otboo.domain.clothes.dto.request.ClothesAttributeRequest;
import com.otboo.domain.clothes.dto.request.ClothesCreateRequest;
import com.otboo.domain.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.domain.clothes.dto.response.ClothesListResponse;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.*;
import com.otboo.domain.clothes.exception.*;
import com.otboo.domain.clothes.llm.ClothesAttributeAutoTagger;
import com.otboo.domain.clothes.mapper.ClothesMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.StorageDirectory;
import com.otboo.global.infrastructure.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothesService {

    private static final int MAX_LIMIT = 100;

    private final ClothesRepository clothesRepository;
    private final ClothesAttributeRepository clothesAttributeRepository;
    private final AttributeSelectableValueRepository selectableValueRepository;
    private final ClothesMapper clothesMapper;
    private final ClothesWriteTransactionalService clothesWriteTransactionalService;
    private final ClothesAttributeAutoTagger clothesAttributeAutoTagger;
    private final FileStorage fileStorage;

    public ClothesResponse create(UUID currentUserId, ClothesCreateRequest request, MultipartFile image) {
        if (!request.ownerId().equals(currentUserId)) {
            throw new AccessDeniedException("본인 명의로만 의상을 등록할 수 있습니다.");
        }

        String imageKey = null;
        ClothesCreateRequest requestToSave = request;
        if (image != null && !image.isEmpty()) {
            StoredFile storedFile = fileStorage.upload(StorageDirectory.CLOTHES, currentUserId, image);
            imageKey = storedFile.objectKey();

            requestToSave = mergeWithAutoTaggedAttributes(request, image);
        }

        return clothesWriteTransactionalService.create(requestToSave, imageKey);
    }

    private ClothesCreateRequest mergeWithAutoTaggedAttributes(ClothesCreateRequest request, MultipartFile image) {
        List<ClothesAttributeRequest> providedAttributes =
                request.attributes() == null ? List.of() : request.attributes();

        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException e) {
            log.warn("이미지 바이트 읽기 실패, 자동 태깅 없이 진행합니다.", e);
            return request;
        }

        List<ClothesAttributeRequest> taggedAttributes = clothesAttributeAutoTagger
                .tagMissingRequiredAttributes(providedAttributes, imageBytes, image.getContentType());

        if (taggedAttributes.isEmpty()) {
            return request;
        }

        List<ClothesAttributeRequest> mergedAttributes = new ArrayList<>(providedAttributes);
        mergedAttributes.addAll(taggedAttributes);

        return new ClothesCreateRequest(request.ownerId(), request.name(), request.type(), mergedAttributes);
    }

    public ClothesResponse update(UUID currentUserId, UUID clothesId, ClothesUpdateRequest request, MultipartFile image) {
        Clothes clothes = clothesRepository.findById(clothesId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new ClothesNotFoundException(clothesId));

        if (!clothes.getOwner().getId().equals(currentUserId)) {
            throw new AccessDeniedException("본인 의상만 수정할 수 있습니다.");
        }

        String newImageKey = null;
        if (image != null && !image.isEmpty()){
            StoredFile storedFile = fileStorage.upload(StorageDirectory.CLOTHES, currentUserId, image);
            newImageKey = storedFile.objectKey();
        }
        return clothesWriteTransactionalService.update(currentUserId, clothesId, request, newImageKey);
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

        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidClothesLimitException(limit);
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
                data, nextCursor, nextIdAfter, hasNext, totalCount, "createdAt","DESCENDING");
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

    @Transactional
    public void delete(UUID currentUserId, UUID clothesId){
        Clothes clothes = clothesRepository.findById(clothesId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new ClothesNotFoundException(clothesId));

        if (!clothes.getOwner().getId().equals(currentUserId)) {
            throw new AccessDeniedException("본인 의상만 삭제할 수 있습니다.");
        }

        clothes.delete();
    }
}
