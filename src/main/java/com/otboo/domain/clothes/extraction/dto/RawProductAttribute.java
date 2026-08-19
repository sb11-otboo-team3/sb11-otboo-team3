package com.otboo.domain.clothes.extraction.dto;

import java.util.List;

public record RawProductAttribute(
        String name,
        List<String> values
) {
}
