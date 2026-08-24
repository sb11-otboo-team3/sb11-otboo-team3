package com.otboo.domain.feed.core.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.clothes.dto.response.ClothesAttributeResponse;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedCoreResponseDtoTest {

  @Test
  @DisplayName("피드 응답 DTO 생성")
  void feedDto_success() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();
    UUID definitionId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-13T01:00:00Z");
    Instant updatedAt = Instant.parse("2026-08-13T02:00:00Z");

    UserSummary author = new UserSummary(authorId, "author", "profile-image");

    WeatherSummaryDto weather = new WeatherSummaryDto(
        weatherId,
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new TemperatureDto(20.0, 0.0, 18.0, 25.0, 20.0)
    );

    ClothesAttributeResponse attribute = new ClothesAttributeResponse(
        definitionId,
        "색상",
        List.of("빨강", "파랑"),
        "빨강"
    );

    FeedOotdDto ootd = new FeedOotdDto(
        clothesId,
        "테스트 상의",
        "image-key",
        ClothesType.TOP,
        List.of(attribute)
    );

    FeedDto feedDto = new FeedDto(
        feedId,
        createdAt,
        updatedAt,
        author,
        weather,
        List.of(ootd),
        "피드 내용",
        10L,
        3,
        true
    );

    assertThat(feedDto.id()).isEqualTo(feedId);
    assertThat(feedDto.createdAt()).isEqualTo(createdAt);
    assertThat(feedDto.updatedAt()).isEqualTo(updatedAt);
    assertThat(feedDto.author()).isEqualTo(author);
    assertThat(feedDto.weather()).isEqualTo(weather);
    assertThat(feedDto.ootds()).containsExactly(ootd);
    assertThat(feedDto.content()).isEqualTo("피드 내용");
    assertThat(feedDto.likeCount()).isEqualTo(10L);
    assertThat(feedDto.commentCount()).isEqualTo(3);
    assertThat(feedDto.likedByMe()).isTrue();

    assertThat(ootd.clothesId()).isEqualTo(clothesId);
    assertThat(ootd.name()).isEqualTo("테스트 상의");
    assertThat(ootd.imageUrl()).isEqualTo("image-key");
    assertThat(ootd.type()).isEqualTo(ClothesType.TOP);
    assertThat(ootd.attributes()).containsExactly(attribute);
  }

  @Test
  @DisplayName("피드 커서 응답 DTO 생성")
  void feedDtoCursorResponse_success() {
    UUID nextIdAfter = UUID.randomUUID();

    FeedDtoCursorResponse response = new FeedDtoCursorResponse(
        List.of(),
        "2026-08-13T01:00:00Z",
        nextIdAfter,
        false,
        0L,
        "createdAt",
        "DESCENDING"
    );

    assertThat(response.data()).isEmpty();
    assertThat(response.nextCursor()).isEqualTo("2026-08-13T01:00:00Z");
    assertThat(response.nextIdAfter()).isEqualTo(nextIdAfter);
    assertThat(response.hasNext()).isFalse();
    assertThat(response.totalCount()).isZero();
    assertThat(response.sortBy()).isEqualTo("createdAt");
    assertThat(response.sortDirection()).isEqualTo("DESCENDING");
  }
}