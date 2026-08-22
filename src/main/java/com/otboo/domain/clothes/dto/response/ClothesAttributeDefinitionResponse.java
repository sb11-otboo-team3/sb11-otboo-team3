package com.otboo.domain.clothes.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClothesAttributeDefinitionResponse(
        UUID id,
        String name,
        List<String> selectableValues,
        boolean required,
        Instant createdAt
) {
}
