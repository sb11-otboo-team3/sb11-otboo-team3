package com.otboo.domain.clothes.controller;

import com.otboo.domain.clothes.dto.request.ClothesCreateRequest;
import com.otboo.domain.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.domain.clothes.dto.response.ClothesListResponse;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.service.ClothesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;


@RestController
@RequestMapping("/api/clothes")
@RequiredArgsConstructor
public class ClothesController {

    private final ClothesService clothesService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesResponse> create(
            @AuthenticationPrincipal UUID currentUserId,
            @RequestPart("request") @Valid ClothesCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        ClothesResponse response = clothesService.create(currentUserId, request, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public  ResponseEntity<ClothesListResponse> getList(
            @AuthenticationPrincipal UUID currentUserId,
            @RequestParam UUID ownerId,
            @RequestParam(required = false) ClothesType typeEqual,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam int limit
    ) {
        ClothesListResponse response = clothesService.getList(
                currentUserId, ownerId, typeEqual, cursor, idAfter, limit
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/{clothesId}", consumes =
            MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesResponse> update(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID clothesId,
            @RequestPart("request") @Valid ClothesUpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        ClothesResponse response = clothesService.update(currentUserId, clothesId, request, image);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{clothesId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID clothesId
    ) {
        clothesService.delete(currentUserId, clothesId);
        return ResponseEntity.noContent().build();
    }
}
