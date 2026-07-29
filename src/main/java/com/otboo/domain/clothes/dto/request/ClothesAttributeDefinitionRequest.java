package com.otboo.domain.clothes.dto.request;

import java.util.List;

public record ClothesAttributeDefinitionRequest(
        String name,
        List<String> selectableValues
) {
}
