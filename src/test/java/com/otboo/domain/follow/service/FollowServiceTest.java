package com.otboo.domain.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.follow.dto.response.FollowSummaryDto;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.follow.exception.DuplicateFollowException;
import com.otboo.domain.follow.exception.FollowForbiddenException;
import com.otboo.domain.follow.exception.FollowNotFoundException;
import com.otboo.domain.follow.exception.FollowUserNotFoundException;
import com.otboo.domain.follow.exception.SelfFollowNotAllowedException;
import com.otboo.domain.follow.repository.FollowRepository;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  @Mock
  private FollowRepository followRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private FollowService followService;

  @Test
  @DisplayName("팔로우 성공 테스트")
  void createFollow_success() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("followee@test.com", "followee", "password");

    FollowCreateRequest request = new FollowCreateRequest(followerId, followeeId);

    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
    given(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId))
        .willReturn(false);
    given(followRepository.save(any(Follow.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    FollowDto result = followService.createFollow(request, followerId);

    assertThat(result.follower().name()).isEqualTo("follower");
    assertThat(result.followee().name()).isEqualTo("followee");

    verify(followRepository).save(any(Follow.class));
    verify(eventPublisher).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("자기자신을 팔로우시 예외 테스트")
  void createFollow_selfFollow_throwsException() {
    UUID userId = UUID.randomUUID();
    FollowCreateRequest request = new FollowCreateRequest(userId, userId);

    assertThatThrownBy(() -> followService.createFollow(request, userId))
        .isInstanceOf(SelfFollowNotAllowedException.class);

    // 예외 발생해서 save가 호출되면 안됨
    verify(followRepository, never()).save(any());
  }

  @Test
  @DisplayName("사용자(팔로워)가 DB에 없는 경우 예외 테스트")
  void createFollow_followerNotFound_throwsException() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    FollowCreateRequest request = new FollowCreateRequest(followerId, followeeId);

    // 팔로워 사용자가 DB에 없는 상황
    given(userRepository.findById(followerId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.createFollow(request, followerId))
        .isInstanceOf(FollowUserNotFoundException.class);

    // 예외 발생해서 save가 호출되면 안됨
    verify(followRepository, never()).save(any());
  }

  @Test
  @DisplayName("사용자(팔로위)가 DB에 없는 경우 예외 테스트")
  void createFollow_followeeNotFound_throwsException() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    FollowCreateRequest request = new FollowCreateRequest(followerId, followeeId);

    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    // 팔로위 사용자가 DB에 없는 상황
    given(userRepository.findById(followeeId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.createFollow(request, followerId))
        .isInstanceOf(FollowUserNotFoundException.class);

    // 예외 발생해서 save가 호출되면 안됨
    verify(followRepository, never()).save(any());
  }

  @Test
  @DisplayName("이미 팔로우 중인 경우 예외 테스트")
  void createFollow_duplicateFollow_throwsException() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("followee@test.com", "followee", "password");
    FollowCreateRequest request = new FollowCreateRequest(followerId, followeeId);

    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
    // 이미 팔로우한 사용자를 다시 팔로우한 상황
    given(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId))
        .willReturn(true);

    assertThatThrownBy(() -> followService.createFollow(request, followerId))
        .isInstanceOf(DuplicateFollowException.class);

    verify(followRepository, never()).save(any());
  }

  @Test
  @DisplayName("팔로우 취소 성공 테스트")
  void cancelFollow_success() {
    UUID followId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("followee@test.com", "followee", "password");

    ReflectionTestUtils.setField(follower, "id", currentUserId);

    Follow follow = Follow.create(follower, followee);

    given(followRepository.findById(followId)).willReturn(Optional.of(follow));

    followService.cancelFollow(followId, follower.getId());

    verify(followRepository).delete(follow);
  }

  @Test
  @DisplayName("존재하지 않는 팔로우를 취소시 예외 테스트")
  void cancelFollow_notFound_throwsException() {
    UUID followId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    given(followRepository.findById(followId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.cancelFollow(followId, null))
        .isInstanceOf(FollowNotFoundException.class);

    verify(followRepository, never()).delete(any());
  }

  @Test
  @DisplayName("팔로우한 사용자가 아닌 경우 예외 테스트")
  void cancelFollow_forbidden_throwsException() {
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("followee@test.com", "followee", "password");

    ReflectionTestUtils.setField(follower, "id", followerId);

    Follow follow = Follow.create(follower, followee);

    given(followRepository.findById(followId)).willReturn(Optional.of(follow));

    assertThatThrownBy(() -> followService.cancelFollow(followId, currentUserId))
        .isInstanceOf(FollowForbiddenException.class);

    verify(followRepository, never()).delete(any());
  }

  @Test
  @DisplayName("팔로잉 목록 조회 성공 테스트(다음 페이지 존재)")
  void getFollowings_success_hasNext() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId1 = UUID.randomUUID();
    UUID followeeId2 = UUID.randomUUID();
    UUID followeeId3 = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee1 = User.create("a@test.com", "Alice", "password");
    User followee2 = User.create("b@test.com", "Bob", "password");
    User followee3 = User.create("c@test.com", "Charlie", "password");

    Follow follow1 = Follow.create(follower, followee1);
    Follow follow2 = Follow.create(follower, followee2);
    Follow follow3 = Follow.create(follower, followee3);

    given(userRepository.existsById(followerId)).willReturn(true);
    given(followRepository.findFollowings(followerId, null, null, 3, null))
        .willReturn(List.of(follow1, follow2, follow3));
    given(followRepository.countFollowings(followerId, null))
        .willReturn(3L);

    FollowListResponse result = followService.getFollowings(
        followerId,
        null,
        null,
        2,
        null
    );

    assertThat(result.data()).hasSize(2);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo("bob");
    assertThat(result.nextIdAfter()).isEqualTo(follow2.getId());
    assertThat(result.totalCount()).isEqualTo(3L);
    assertThat(result.sortBy()).isEqualTo("name");
    assertThat(result.sortDirection()).isEqualTo("ASCENDING");

    verify(followRepository).findFollowings(followerId, null, null, 3, null);
    verify(followRepository).countFollowings(followerId, null);
  }

  @Test
  @DisplayName("팔로잉 목록 조회 성공 테스트(다음 페이지 없음)")
  void getFollowings_success_hasNoNext() {
    UUID followerId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("a@test.com", "Alice", "password");

    Follow follow = Follow.create(follower, followee);

    given(userRepository.existsById(followerId)).willReturn(true);
    given(followRepository.findFollowings(followerId, null, null, 3, null))
        .willReturn(List.of(follow));
    given(followRepository.countFollowings(followerId, null))
        .willReturn(1L);

    FollowListResponse result = followService.getFollowings(
        followerId,
        null,
        null,
        2,
        null
    );

    assertThat(result.data()).hasSize(1);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.nextIdAfter()).isNull();
    assertThat(result.totalCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("팔로잉 목록 조회 실패 테스트(followerId가 없음)")
  void getFollowings_followerNotFound_throwsException() {
    UUID followerId = UUID.randomUUID();

    given(userRepository.existsById(followerId)).willReturn(false);

    assertThatThrownBy(() -> followService.getFollowings(
        followerId,
        null,
        null,
        2,
        null
    ))
        .isInstanceOf(FollowUserNotFoundException.class);

    verify(followRepository, never()).findFollowings(any(), any(), any(), anyInt(), any());
    verify(followRepository, never()).countFollowings(any(), any());
  }

  @Test
  @DisplayName("팔로워 목록 조회 성공 테스트(다음 페이지 존재)")
  void getFollowers_success_hasNext() {
    UUID followeeId = UUID.randomUUID();

    User followee = User.create("followee@test.com", "followee", "password");
    User follower1 = User.create("a@test.com", "Alice", "password");
    User follower2 = User.create("b@test.com", "Bob", "password");
    User follower3 = User.create("c@test.com", "Charlie", "password");

    Follow follow1 = Follow.create(follower1, followee);
    Follow follow2 = Follow.create(follower2, followee);
    Follow follow3 = Follow.create(follower3, followee);

    given(userRepository.existsById(followeeId)).willReturn(true);
    given(followRepository.findFollowers(followeeId, null, null, 3, null))
        .willReturn(List.of(follow1, follow2, follow3));
    given(followRepository.countFollowers(followeeId, null))
        .willReturn(3L);

    FollowListResponse result = followService.getFollowers(
        followeeId,
        null,
        null,
        2,
        null
    );

    assertThat(result.data()).hasSize(2);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo("bob");
    assertThat(result.nextIdAfter()).isEqualTo(follow2.getId());
    assertThat(result.totalCount()).isEqualTo(3L);

    verify(followRepository).findFollowers(followeeId, null, null, 3, null);
    verify(followRepository).countFollowers(followeeId, null);
  }

  @Test
  @DisplayName("팔로워 목록 조회 성공 테스트(다음 페이지 없음)")
  void getFollowers_success_hasNoNext() {
    UUID followeeId = UUID.randomUUID();

    User followee = User.create("followee@test.com", "followee", "password");
    User follower = User.create("a@test.com", "Alice", "password");

    Follow follow = Follow.create(follower, followee);

    given(userRepository.existsById(followeeId)).willReturn(true);
    given(followRepository.findFollowers(followeeId, null, null, 3, null))
        .willReturn(List.of(follow));
    given(followRepository.countFollowers(followeeId, null))
        .willReturn(1L);

    FollowListResponse result = followService.getFollowers(
        followeeId,
        null,
        null,
        2,
        null
    );

    assertThat(result.data()).hasSize(1);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.nextIdAfter()).isNull();
    assertThat(result.totalCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("팔로잉 목록 조회 실패 테스트(followeeId가 없음)")
  void getFollowers_followeeNotFound_throwsException() {
    UUID followeeId = UUID.randomUUID();

    given(userRepository.existsById(followeeId)).willReturn(false);

    assertThatThrownBy(() -> followService.getFollowers(
        followeeId,
        null,
        null,
        2,
        null
    ))
        .isInstanceOf(FollowUserNotFoundException.class);

    verify(followRepository, never()).findFollowers(any(), any(), any(), anyInt(), any());
    verify(followRepository, never()).countFollowers(any(), any());
  }

  @Test
  @DisplayName("팔로우 요약 조회 성공 테스트(맞팔인 경우)")
  void getFollowSummary_success_followedByMeAndFollowingMe() {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID followedByMeId = UUID.randomUUID();

    User currentUser = User.create("current@test.com", "current", "password");
    User targetUser = User.create("target@test.com", "target", "password");

    ReflectionTestUtils.setField(currentUser, "id", currentUserId);
    ReflectionTestUtils.setField(targetUser, "id", userId);

    Follow followedByMeFollow = Follow.create(currentUser, targetUser);
    ReflectionTestUtils.setField(followedByMeFollow, "id", followedByMeId);

    given(userRepository.existsById(userId)).willReturn(true);
    given(followRepository.findByFollowerIdAndFolloweeId(currentUserId, userId))
        .willReturn(Optional.of(followedByMeFollow));
    given(followRepository.existsByFollowerIdAndFolloweeId(userId, currentUserId))
        .willReturn(true);
    given(followRepository.countFollowers(userId, null)).willReturn(5L);
    given(followRepository.countFollowings(userId, null)).willReturn(3L);

    FollowSummaryDto result = followService.getFollowSummary(userId, currentUserId);

    assertThat(result.followeeId()).isEqualTo(userId);
    assertThat(result.followerCount()).isEqualTo(5L);
    assertThat(result.followingCount()).isEqualTo(3L);
    assertThat(result.followedByMe()).isTrue();
    assertThat(result.followedByMeId()).isEqualTo(followedByMeId);
    assertThat(result.followingMe()).isTrue();
  }

  @Test
  @DisplayName("팔로우 요약 조회 성공 테스트(맞팔 아닌 경우)")
  void getFollowSummary_success_noRelationship() {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    given(userRepository.existsById(userId)).willReturn(true);
    given(followRepository.findByFollowerIdAndFolloweeId(currentUserId, userId))
        .willReturn(Optional.empty());
    given(followRepository.existsByFollowerIdAndFolloweeId(userId, currentUserId))
        .willReturn(false);
    given(followRepository.countFollowers(userId, null)).willReturn(0L);
    given(followRepository.countFollowings(userId, null)).willReturn(0L);

    FollowSummaryDto result = followService.getFollowSummary(userId, currentUserId);

    assertThat(result.followeeId()).isEqualTo(userId);
    assertThat(result.followerCount()).isEqualTo(0L);
    assertThat(result.followingCount()).isEqualTo(0L);
    assertThat(result.followedByMe()).isFalse();
    assertThat(result.followedByMeId()).isNull();
    assertThat(result.followingMe()).isFalse();
  }

  @Test
  @DisplayName("조회 대상이 없어서 팔로우 요약 조회 실패 테스트")
  void getFollowSummary_userNotFound_throwsException() {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    given(userRepository.existsById(userId)).willReturn(false);

    assertThatThrownBy(() -> followService.getFollowSummary(userId, currentUserId))
        .isInstanceOf(FollowUserNotFoundException.class);

    verify(followRepository, never()).findByFollowerIdAndFolloweeId(any(), any());
    verify(followRepository, never()).existsByFollowerIdAndFolloweeId(any(), any());
    verify(followRepository, never()).countFollowers(any(), any());
    verify(followRepository, never()).countFollowings(any(), any());
  }
}