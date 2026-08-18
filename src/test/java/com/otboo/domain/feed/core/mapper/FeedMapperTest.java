package com.otboo.domain.feed.core.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.otboo.domain.clothes.dto.response.ClothesAttributeResponse;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.feed.clothes.entity.FeedClothes;
import com.otboo.domain.feed.core.dto.response.FeedDto;
import com.otboo.domain.feed.core.dto.response.FeedOotdDto;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.global.infrastructure.storage.FileStorage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedMapperTest {

  @Mock
  private UserSummaryMapper userSummaryMapper;

  @Mock
  private FileStorage fileStorage;

  @InjectMocks
  private FeedMapper feedMapper;

  @Test
  @DisplayName("피드 DTO 변환 성공")
  void toDto_success() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();
    UUID definitionId = UUID.randomUUID();

    Instant createdAt = Instant.parse("2026-08-13T01:00:00Z");
    Instant updatedAt = Instant.parse("2026-08-13T02:00:00Z");

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    UserSummary authorSummary = new UserSummary(authorId, "author", "profile-image");
    given(userSummaryMapper.toUserSummary(author)).willReturn(authorSummary);
    given(fileStorage.generateReadUrl("clothes-image-key"))
        .willReturn("https://example.com/clothes-image.jpg");

    WeatherSummaryDto weatherSummary = new WeatherSummaryDto(
        weatherId,
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new TemperatureDto(20.0, 0.0, 18.0, 25.0)
    );

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        mock(JsonNode.class),
        "피드 내용"
    );
    ReflectionTestUtils.setField(feed, "id", feedId);
    ReflectionTestUtils.setField(feed, "createdAt", createdAt);
    ReflectionTestUtils.setField(feed, "updatedAt", updatedAt);
    ReflectionTestUtils.setField(feed, "likeCount", 3L);
    ReflectionTestUtils.setField(feed, "commentCount", 2);

    Clothes clothes = new Clothes(
        author,
        "테스트 상의",
        "clothes-image-key",
        ClothesType.TOP
    );
    ReflectionTestUtils.setField(clothes, "id", clothesId);

    FeedClothes feedClothes = FeedClothes.create(feed, clothes);

    ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
    ReflectionTestUtils.setField(definition, "id", definitionId);

    ClothesAttribute attribute = new ClothesAttribute(clothes, definition, "빨강");

    FeedDto result = feedMapper.toDto(
        feed,
        weatherSummary,
        List.of(feedClothes),
        Map.of(clothesId, List.of(attribute)),
        Map.of(definitionId, List.of("빨강", "파랑")),
        true
    );

    assertThat(result.id()).isEqualTo(feedId);
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.updatedAt()).isEqualTo(updatedAt);
    assertThat(result.author()).isEqualTo(authorSummary);
    assertThat(result.weather()).isEqualTo(weatherSummary);
    assertThat(result.content()).isEqualTo("피드 내용");
    assertThat(result.likeCount()).isEqualTo(3L);
    assertThat(result.commentCount()).isEqualTo(2);
    assertThat(result.likedByMe()).isTrue();

    assertThat(result.ootds()).hasSize(1);

    FeedOotdDto ootd = result.ootds().get(0);
    assertThat(ootd.clothesId()).isEqualTo(clothesId);
    assertThat(ootd.name()).isEqualTo("테스트 상의");
    assertThat(ootd.imageUrl()).isEqualTo("https://example.com/clothes-image.jpg");
    assertThat(ootd.type()).isEqualTo(ClothesType.TOP);

    assertThat(ootd.attributes()).hasSize(1);

    ClothesAttributeResponse attributeResponse = ootd.attributes().get(0);
    assertThat(attributeResponse.definitionId()).isEqualTo(definitionId);
    assertThat(attributeResponse.definitionName()).isEqualTo("색상");
    assertThat(attributeResponse.selectableValues()).containsExactly("빨강", "파랑");
    assertThat(attributeResponse.value()).isEqualTo("빨강");

    verify(userSummaryMapper).toUserSummary(author);
    verify(fileStorage).generateReadUrl("clothes-image-key");
  }

  @Test
  @DisplayName("옷 속성이 없으면 빈 속성 목록으로 변환한다")
  void toDto_withoutAttributes_success() {
    UUID clothesId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    UserSummary authorSummary = new UserSummary(UUID.randomUUID(), "author", null);
    given(userSummaryMapper.toUserSummary(author)).willReturn(authorSummary);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        mock(JsonNode.class),
        "피드 내용"
    );

    Clothes clothes = new Clothes(
        author,
        "테스트 바지",
        null,
        ClothesType.BOTTOM
    );
    ReflectionTestUtils.setField(clothes, "id", clothesId);

    FeedClothes feedClothes = FeedClothes.create(feed, clothes);

    WeatherSummaryDto weatherSummary = mock(WeatherSummaryDto.class);

    FeedDto result = feedMapper.toDto(
        feed,
        weatherSummary,
        List.of(feedClothes),
        Map.of(),
        Map.of(),
        false
    );

    assertThat(result.ootds()).hasSize(1);
    assertThat(result.ootds().get(0).attributes()).isEmpty();
    assertThat(result.ootds().get(0).imageUrl()).isNull();
    assertThat(result.likedByMe()).isFalse();

    verify(userSummaryMapper).toUserSummary(author);
    verify(fileStorage, never()).generateReadUrl(any());
  }
}