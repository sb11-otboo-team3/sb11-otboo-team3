package com.otboo.domain.feed.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;

import com.otboo.domain.feed.core.exception.InvalidFeedCursorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.feed.clothes.repository.FeedClothesRepository;
import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.core.dto.response.FeedDto;
import com.otboo.domain.feed.core.dto.response.FeedDtoCursorResponse;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.mapper.FeedMapper;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedQueryServiceTest {

  @Mock
  private FeedRepository feedRepository;

  @Mock
  private FeedClothesRepository feedClothesRepository;

  @Mock
  private FeedLikeRepository feedLikeRepository;

  @Mock
  private ClothesAttributeRepository clothesAttributeRepository;

  @Mock
  private AttributeSelectableValueRepository attributeSelectableValueRepository;

  @Mock
  private FeedMapper feedMapper;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private FeedQueryService feedQueryService;
  @Test
  @DisplayName("피드 목록 조회 성공")
  void getFeeds_success() {
    UUID currentUserId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", currentUserId);

    WeatherSummaryDto weatherSummary = new WeatherSummaryDto(
        UUID.randomUUID(),
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new TemperatureDto(20.0, 0.0, 18.0, 25.0)
    );

    Feed feed1 = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.valueToTree(weatherSummary),
        "첫 번째 피드"
    );
    UUID feed1Id = UUID.randomUUID();
    Instant feed1CreatedAt = Instant.parse("2026-08-10T09:00:00Z");
    ReflectionTestUtils.setField(feed1, "id", feed1Id);
    ReflectionTestUtils.setField(feed1, "createdAt", feed1CreatedAt);

    Feed feed2 = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.valueToTree(weatherSummary),
        "두 번째 피드"
    );
    ReflectionTestUtils.setField(feed2, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(feed2, "createdAt", Instant.parse("2026-08-10T08:00:00Z"));

    FeedDto feedDto = mock(FeedDto.class);

    given(feedRepository.findFeeds(
        null,
        null,
        2,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null
    )).willReturn(List.of(feed1, feed2));

    given(feedLikeRepository.findByFeedIdInAndUserId(anyList(), eq(currentUserId)))
        .willReturn(List.of());
    given(feedClothesRepository.findByFeedInAndClothesDeletedAtIsNull(anyList()))
        .willReturn(List.of());
    given(clothesAttributeRepository.findByClothesIn(anyList()))
        .willReturn(List.of());
    given(attributeSelectableValueRepository
        .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
        .willReturn(List.of());
    given(feedMapper.toDto(any(), any(), anyList(), anyMap(), anyMap(), anyBoolean()))
        .willReturn(feedDto);
    given(feedRepository.countFeeds(null, null, null, null))
        .willReturn(2L);

    FeedDtoCursorResponse result = feedQueryService.getFeeds(
        null,
        null,
        1,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null,
        currentUserId
    );

    assertThat(result.data()).containsExactly(feedDto);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(feed1CreatedAt.toString());
    assertThat(result.nextIdAfter()).isEqualTo(feed1Id);
    assertThat(result.totalCount()).isEqualTo(2L);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo("DESCENDING");

    verify(feedRepository).findFeeds(
        null,
        null,
        2,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null
    );
  }

  @Test
  @DisplayName("피드 목록 조회 성공 - 빈 목록")
  void getFeeds_empty_success() {
    UUID currentUserId = UUID.randomUUID();

    given(feedRepository.findFeeds(
        null,
        null,
        21,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null
    )).willReturn(List.of());

    given(feedRepository.countFeeds(null, null, null, null))
        .willReturn(0L);

    FeedDtoCursorResponse result = feedQueryService.getFeeds(
        null,
        null,
        20,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null,
        currentUserId
    );

    assertThat(result.data()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.nextIdAfter()).isNull();
    assertThat(result.totalCount()).isZero();

    verify(feedLikeRepository, never()).findByFeedIdInAndUserId(anyList(), eq(currentUserId));
    verify(feedClothesRepository, never()).findByFeedInAndClothesDeletedAtIsNull(anyList());
  }

  @Test
  @DisplayName("피드 목록 조회 성공 - likeCount 정렬 커서 반환")
  void getFeeds_likeCountCursor_success() {
    UUID currentUserId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");

    WeatherSummaryDto weatherSummary = new WeatherSummaryDto(
        UUID.randomUUID(),
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new TemperatureDto(20.0, 0.0, 18.0, 25.0)
    );

    Feed feed1 = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.valueToTree(weatherSummary),
        "좋아요 정렬 피드"
    );
    ReflectionTestUtils.setField(feed1, "id", feedId);
    ReflectionTestUtils.setField(feed1, "likeCount", 10L);

    Feed feed2 = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.valueToTree(weatherSummary),
        "다음 페이지 확인용 피드"
    );
    ReflectionTestUtils.setField(feed2, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(feed2, "likeCount", 5L);

    FeedDto feedDto = mock(FeedDto.class);

    given(feedRepository.findFeeds(
        null,
        null,
        2,
        SortBy.likeCount,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null
    )).willReturn(List.of(feed1, feed2));

    given(feedLikeRepository.findByFeedIdInAndUserId(anyList(), eq(currentUserId)))
        .willReturn(List.of());
    given(feedClothesRepository.findByFeedInAndClothesDeletedAtIsNull(anyList()))
        .willReturn(List.of());
    given(clothesAttributeRepository.findByClothesIn(anyList()))
        .willReturn(List.of());
    given(attributeSelectableValueRepository
        .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
        .willReturn(List.of());
    given(feedMapper.toDto(any(), any(), anyList(), anyMap(), anyMap(), anyBoolean()))
        .willReturn(feedDto);
    given(feedRepository.countFeeds(null, null, null, null))
        .willReturn(2L);

    FeedDtoCursorResponse result = feedQueryService.getFeeds(
        null,
        null,
        1,
        SortBy.likeCount,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null,
        currentUserId
    );

    assertThat(result.data()).containsExactly(feedDto);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo("10");
    assertThat(result.nextIdAfter()).isEqualTo(feedId);
    assertThat(result.sortBy()).isEqualTo("likeCount");
  }

  @Test
  @DisplayName("피드 목록 조회 실패 - cursor와 idAfter가 함께 오지 않음")
  void getFeeds_invalidCursorPair() {
    UUID currentUserId = UUID.randomUUID();

    assertThatThrownBy(() -> feedQueryService.getFeeds(
        "2026-08-13T01:00:00Z",
        null,
        20,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null,
        currentUserId
    )).isInstanceOf(InvalidFeedCursorException.class);

    verify(feedRepository, never()).findFeeds(
        any(),
        any(),
        any(Integer.class),
        any(),
        any(),
        any(),
        any(),
        any(),
        any()
    );
  }

  @Test
  @DisplayName("피드 목록 조회 실패 - cursor 형식 오류")
  void getFeeds_invalidCursorFormat() {
    UUID currentUserId = UUID.randomUUID();

    assertThatThrownBy(() -> feedQueryService.getFeeds(
        "invalid-cursor",
        UUID.randomUUID(),
        20,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null,
        currentUserId
    )).isInstanceOf(InvalidFeedCursorException.class);

    verify(feedRepository, never()).findFeeds(
        any(),
        any(),
        any(Integer.class),
        any(),
        any(),
        any(),
        any(),
        any(),
        any()
    );
  }
}
