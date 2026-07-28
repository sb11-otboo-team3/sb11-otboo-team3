package com.otboo.domain.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.follow.exception.DuplicateFollowException;
import com.otboo.domain.follow.exception.FollowUserNotFoundException;
import com.otboo.domain.follow.exception.SelfFollowNotAllowedException;
import com.otboo.domain.follow.repository.FollowRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
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
  void createFollow_selfFollow_throwsException() {
    UUID userId = UUID.randomUUID();
    FollowCreateRequest request = new FollowCreateRequest(userId, userId);

    assertThatThrownBy(() -> followService.createFollow(request))
        .isInstanceOf(SelfFollowNotAllowedException.class);

    // 예외 발생해서 save가 호출되면 안됨
    verify(followRepository, never()).save(any());
  }

  @Test
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
}