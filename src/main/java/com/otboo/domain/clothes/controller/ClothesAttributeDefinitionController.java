package com.otboo.domain.clothes.controller;

import com.otboo.domain.clothes.dto.request.ClothesAttributeDefinitionRequest;
import com.otboo.domain.clothes.dto.response.ClothesAttributeDefinitionResponse;
import com.otboo.domain.clothes.service.ClothesAttributeDefinitionService;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/clothes/attribute-defs")
@RequiredArgsConstructor
public class ClothesAttributeDefinitionController {

    private final ClothesAttributeDefinitionService clothesAttributeDefinitionService;

    @GetMapping
    public ResponseEntity<List<ClothesAttributeDefinitionResponse>> getList(
            @RequestParam String sortBy,
            @RequestParam String sortDirection,
            @RequestParam(required = false) String keywordLike
    ) {
        List<ClothesAttributeDefinitionResponse> responses =
                clothesAttributeDefinitionService.getList(sortBy, sortDirection, keywordLike);
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClothesAttributeDefinitionResponse> create(
            @Valid @RequestBody ClothesAttributeDefinitionRequest request
            ) {
        ClothesAttributeDefinitionResponse response =
                clothesAttributeDefinitionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{definitionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClothesAttributeDefinitionResponse> update(
            @PathVariable UUID definitionId,
            @Valid @RequestBody ClothesAttributeDefinitionRequest request
    ) {
        ClothesAttributeDefinitionResponse response =
                clothesAttributeDefinitionService.update(definitionId, request);
        return ResponseEntity.ok(response);
    }

}
