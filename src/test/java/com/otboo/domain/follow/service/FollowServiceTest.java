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
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.follow.exception.DuplicateFollowException;
import com.otboo.domain.follow.exception.FollowNotFoundException;
import com.otboo.domain.follow.exception.FollowUserNotFoundException;
import com.otboo.domain.follow.exception.SelfFollowNotAllowedException;
import com.otboo.domain.follow.repository.FollowRepository;
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

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  @Mock
  private FollowRepository followRepository;

  @Mock
  private UserRepository userRepository;

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

    FollowDto result = followService.createFollow(request);

    assertThat(result.follower().name()).isEqualTo("follower");
    assertThat(result.followee().name()).isEqualTo("followee");

    verify(followRepository).save(any(Follow.class));
  }

  @Test
  @DisplayName("자기자신을 팔로우시 예외 테스트")
  void createFollow_selfFollow_throwsException() {
    UUID userId = UUID.randomUUID();
    FollowCreateRequest request = new FollowCreateRequest(userId, userId);

    assertThatThrownBy(() -> followService.createFollow(request))
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

    assertThatThrownBy(() -> followService.createFollow(request))
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

    assertThatThrownBy(() -> followService.createFollow(request))
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

    assertThatThrownBy(() -> followService.createFollow(request))
        .isInstanceOf(DuplicateFollowException.class);

    verify(followRepository, never()).save(any());
  }

  @Test
  @DisplayName("팔로우 취소 성공 테스트")
  void cancelFollow_success() {
    UUID followId = UUID.randomUUID();

    User follower = User.create("follower@test.com", "follower", "password");
    User followee = User.create("followee@test.com", "followee", "password");
    Follow follow = Follow.create(follower, followee);

    given(followRepository.findById(followId)).willReturn(Optional.of(follow));

    followService.cancelFollow(followId);

    verify(followRepository).delete(follow);
  }

  @Test
  @DisplayName("존재하지 않는 팔로우를 취소시 예외 테스트")
  void cancelFollow_notFound_throwsException() {
    UUID followId = UUID.randomUUID();

    given(followRepository.findById(followId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.cancelFollow(followId))
        .isInstanceOf(FollowNotFoundException.class);

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
}