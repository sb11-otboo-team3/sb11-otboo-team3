package com.otboo.domain.follow.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.follow.dto.response.FollowSummaryDto;
import com.otboo.domain.user.dto.UserSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FollowDtoTest {

  @Test
  @DisplayName("팔로우 생성 요청 DTO 생성")
  void followCreateRequest_success() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(followerId, followeeId);

    assertThat(request.followerId()).isEqualTo(followerId);
    assertThat(request.followeeId()).isEqualTo(followeeId);
  }

  @Test
  @DisplayName("팔로우 응답 DTO 생성")
  void followDto_success() {
    UUID followId = UUID.randomUUID();
    UserSummary follower = new UserSummary(UUID.randomUUID(), "follower", null);
    UserSummary followee = new UserSummary(UUID.randomUUID(), "followee", null);

    FollowDto dto = new FollowDto(followId, followee, follower);

    assertThat(dto.id()).isEqualTo(followId);
    assertThat(dto.followee()).isEqualTo(followee);
    assertThat(dto.follower()).isEqualTo(follower);
  }

  @Test
  @DisplayName("팔로우 목록 응답 DTO 생성")
  void followListResponse_success() {
    UUID nextIdAfter = UUID.randomUUID();

    FollowListResponse response = new FollowListResponse(
        List.of(),
        "2026-08-13T01:00:00Z",
        nextIdAfter,
        true,
        5L,
        "createdAt",
        "DESCENDING"
    );

    assertThat(response.data()).isEmpty();
    assertThat(response.nextCursor()).isEqualTo("2026-08-13T01:00:00Z");
    assertThat(response.nextIdAfter()).isEqualTo(nextIdAfter);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.totalCount()).isEqualTo(5L);
    assertThat(response.sortBy()).isEqualTo("createdAt");
    assertThat(response.sortDirection()).isEqualTo("DESCENDING");
  }

  @Test
  @DisplayName("팔로우 요약 응답 DTO 생성")
  void followSummaryDto_success() {
    UUID followeeId = UUID.randomUUID();
    UUID followedByMeId = UUID.randomUUID();

    FollowSummaryDto response = new FollowSummaryDto(
        followeeId,
        10L,
        3L,
        true,
        followedByMeId,
        false
    );

    assertThat(response.followeeId()).isEqualTo(followeeId);
    assertThat(response.followerCount()).isEqualTo(10L);
    assertThat(response.followingCount()).isEqualTo(3L);
    assertThat(response.followedByMe()).isTrue();
    assertThat(response.followedByMeId()).isEqualTo(followedByMeId);
    assertThat(response.followingMe()).isFalse();
  }
}