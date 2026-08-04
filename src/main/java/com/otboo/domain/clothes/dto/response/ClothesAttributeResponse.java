package com.otboo.domain.clothes.dto.response;

import java.util.List;
import java.util.UUID;

public record ClothesAttributeResponse(
        UUID definitionId,
        String definitionName,
        List<String> selectableValues,
        String value
) {
}
