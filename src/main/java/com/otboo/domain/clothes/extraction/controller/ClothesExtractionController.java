package com.otboo.domain.clothes.extraction.controller;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.extraction.service.ClothesExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/clothes/extractions")
@RequiredArgsConstructor
public class ClothesExtractionController {

    private final ClothesExtractionService clothesExtractionService;

    @GetMapping
    public ResponseEntity<ClothesResponse> extract(
            @AuthenticationPrincipal UUID currentUserId,
            @RequestParam String url
    ) {
        ClothesResponse response = clothesExtractionService.extract(url, currentUserId);
        return ResponseEntity.ok(response);
    }
}
