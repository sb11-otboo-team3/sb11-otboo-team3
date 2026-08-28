package com.otboo.domain.feed.core.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedCoreRequestDtoTest {

  @Test
  @DisplayName("피드 생성 요청 DTO 생성")
  void feedCreateRequest_success() {
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "피드 내용"
    );

    assertThat(request.authorId()).isEqualTo(authorId);
    assertThat(request.weatherId()).isEqualTo(weatherId);
    assertThat(request.clothesIds()).containsExactly(clothesId);
    assertThat(request.content()).isEqualTo("피드 내용");
  }

  @Test
  @DisplayName("피드 수정 요청 DTO 생성")
  void feedUpdateRequest_success() {
    FeedUpdateRequest request = new FeedUpdateRequest("수정 내용");

    assertThat(request.content()).isEqualTo("수정 내용");
  }

  @Test
  @DisplayName("피드 정렬 enum 확인")
  void feedSortEnum_success() {
    assertThat(SortBy.valueOf("createdAt")).isEqualTo(SortBy.createdAt);
    assertThat(SortBy.valueOf("likeCount")).isEqualTo(SortBy.likeCount);
    assertThat(SortDirection.valueOf("ASCENDING")).isEqualTo(SortDirection.ASCENDING);
    assertThat(SortDirection.valueOf("DESCENDING")).isEqualTo(SortDirection.DESCENDING);
  }
}