package com.otboo.domain.clothes.controller;

import com.otboo.domain.clothes.dto.request.ClothesCreateRequest;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.service.ClothesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@RestController
@RequestMapping("/api/clothes")
@RequiredArgsConstructor
public class ClothesController {

    private final ClothesService clothesService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesResponse> create(
            @AuthenticationPrincipal UUID currentUserId,
            @RequestPart("request") @Valid ClothesCreateRequest request
    ) {
        ClothesResponse response = clothesService.create(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
