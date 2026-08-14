package com.otboo.domain.recommendation.controller;

import com.otboo.domain.recommendation.dto.response.RecommendationResponse;
import com.otboo.domain.recommendation.service.RecommendationService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<RecommendationResponse> recommend(
            @AuthenticationPrincipal UUID currentUserId,
            @RequestParam(required = false) List<UUID> excludeClothesIds,
            @RequestParam(required = false) UUID weatherId
    ) {
        Set<UUID> excluded = excludeClothesIds == null ? Set.of() : Set.copyOf(excludeClothesIds);
        RecommendationResponse response = recommendationService.recommend(currentUserId, excluded, weatherId);
        return ResponseEntity.ok(response);
    }
}
