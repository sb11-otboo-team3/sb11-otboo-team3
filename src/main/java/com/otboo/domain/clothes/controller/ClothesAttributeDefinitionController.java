package com.otboo.domain.clothes.controller;

import com.otboo.domain.clothes.dto.response.ClothesAttributeDefinitionResponse;
import com.otboo.domain.clothes.service.ClothesAttributeDefinitionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


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
}
