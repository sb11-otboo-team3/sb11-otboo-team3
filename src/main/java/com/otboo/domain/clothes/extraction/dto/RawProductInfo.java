package com.otboo.domain.clothes.extraction.dto;

import java.util.List;

public record RawProductInfo(
        String sourceUrl,
        String name,
        String imageUrl,
        List<String> categoryHints,
        List<RawProductAttribute> attributes
) {
}
