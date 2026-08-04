package com.otboo.domain.feed.dto.response;

import com.otboo.domain.clothes.entity.ClothesType;
import java.util.List;
import java.util.UUID;

public record FeedOotdDto(
    UUID clothesId,
    String name,
    String imageUrl,
    ClothesType type,
    List<FeedClothesAttributeDto> attributes
) {
}
