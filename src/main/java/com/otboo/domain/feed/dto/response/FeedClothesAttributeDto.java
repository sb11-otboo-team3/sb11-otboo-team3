package com.otboo.domain.feed.dto.response;

import java.util.List;
import java.util.UUID;

public record FeedClothesAttributeDto(
    UUID definitionId,
    String definitionName,
    List<String> selectableValues,
    String value
) {
}