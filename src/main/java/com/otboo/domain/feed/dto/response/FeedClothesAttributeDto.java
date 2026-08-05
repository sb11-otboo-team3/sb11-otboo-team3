package com.otboo.domain.feed.dto.response;

import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import java.util.List;
import java.util.UUID;

public record FeedClothesAttributeDto(
    UUID definitionId,
    String definitionName,
    List<String> selectableValues,
    String value
) {

  public static FeedClothesAttributeDto of(
      ClothesAttribute attribute,
      List<String> selectableValues
  ) {
    ClothesAttributeDefinition definition = attribute.getDefinition();

    return new FeedClothesAttributeDto(
        definition.getId(),
        definition.getName(),
        selectableValues,
        attribute.getValue()
    );
  }

}