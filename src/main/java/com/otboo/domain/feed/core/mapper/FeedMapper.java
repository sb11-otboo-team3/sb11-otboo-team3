package com.otboo.domain.feed.core.mapper;

import com.otboo.domain.clothes.dto.response.ClothesAttributeResponse;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.feed.core.dto.response.FeedDto;
import com.otboo.domain.feed.core.dto.response.FeedOotdDto;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.clothes.entity.FeedClothes;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedMapper {

  private final UserSummaryMapper userSummaryMapper;

  public FeedDto toDto(
      Feed feed,
      WeatherSummaryDto weather,
      List<FeedClothes> feedClothes,
      Map<UUID, List<ClothesAttribute>> attributesByClothesId,
      Map<UUID, List<String>> selectableValuesByDefinitionId,
      boolean likedByMe
  ) {
    return new FeedDto(
        feed.getId(),
        feed.getCreatedAt(),
        feed.getUpdatedAt(),
        userSummaryMapper.toUserSummary(feed.getAuthor()),
        weather,
        toOotds(feedClothes, attributesByClothesId, selectableValuesByDefinitionId),
        feed.getContent(),
        feed.getLikeCount(),
        feed.getCommentCount(),
        likedByMe
    );
  }

  private List<FeedOotdDto> toOotds(
      List<FeedClothes> feedClothes,
      Map<UUID, List<ClothesAttribute>> attributesByClothesId,
      Map<UUID, List<String>> selectableValuesByDefinitionId
  ) {
    return feedClothes.stream()
        .map(feedCloth -> toOotdDto(
            feedCloth,
            attributesByClothesId,
            selectableValuesByDefinitionId
        ))
        .toList();
  }

  private FeedOotdDto toOotdDto(
      FeedClothes feedClothes,
      Map<UUID, List<ClothesAttribute>> attributesByClothesId,
      Map<UUID, List<String>> selectableValuesByDefinitionId
  ) {
    Clothes clothes = feedClothes.getClothes();

    return new FeedOotdDto(
        clothes.getId(),
        clothes.getName(),
        clothes.getImageKey(),
        clothes.getType(),
        toAttributeResponses(
            attributesByClothesId.getOrDefault(clothes.getId(), List.of()),
            selectableValuesByDefinitionId
        )
    );
  }

  private List<ClothesAttributeResponse> toAttributeResponses(
      List<ClothesAttribute> attributes,
      Map<UUID, List<String>> selectableValuesByDefinitionId
  ) {
    return attributes.stream()
        .map(attribute -> toAttributeResponse(attribute, selectableValuesByDefinitionId))
        .toList();
  }

  private ClothesAttributeResponse toAttributeResponse(
      ClothesAttribute attribute,
      Map<UUID, List<String>> selectableValuesByDefinitionId
  ) {
    UUID definitionId = attribute.getDefinition().getId();

    return new ClothesAttributeResponse(
        definitionId,
        attribute.getDefinition().getName(),
        selectableValuesByDefinitionId.getOrDefault(definitionId, List.of()),
        attribute.getValue()
    );
  }
}