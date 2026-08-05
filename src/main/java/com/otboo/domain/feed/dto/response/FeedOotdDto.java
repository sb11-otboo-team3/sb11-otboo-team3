package com.otboo.domain.feed.dto.response;

import com.otboo.domain.clothes.entity.Clothes;
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
  public static FeedOotdDto of(
      Clothes clothes,
      List<FeedClothesAttributeDto> attributes
  ) {
    return new FeedOotdDto(
        clothes.getId(),
        clothes.getName(),
        clothes.getImageUrl(),
        clothes.getType(),
        attributes
    );
  }
}
