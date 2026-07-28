package com.otboo.domain.follow.service;

import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.follow.exception.DuplicateFollowException;
import com.otboo.domain.follow.exception.FollowUserNotFoundException;
import com.otboo.domain.follow.exception.SelfFollowNotAllowedException;
import com.otboo.domain.follow.repository.FollowRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

  private final FollowRepository followRepository;
  private final UserRepository userRepository;

  @Transactional
  public FollowDto createFollow(FollowCreateRequest request) {
    // 자기자신 팔로우 예외처리
    if (request.followerId().equals(request.followeeId())) {
      throw new SelfFollowNotAllowedException();
    }

    // 팔로우 사용자 존재여부 예외처리
    User follower = userRepository.findById(request.followerId())
        .orElseThrow(() -> new FollowUserNotFoundException(request.followerId()));
    User followee = userRepository.findById(request.followeeId())
        .orElseThrow(() -> new FollowUserNotFoundException(request.followeeId()));

    // 이미 팔로우 중일경우 예외처리
    if (followRepository.existsByFollowerIdAndFolloweeId(
        request.followerId(), request.followeeId()
    )) {
      throw new DuplicateFollowException();
    }

    Follow follow = Follow.create(follower, followee);
    Follow savedFollow = followRepository.save(follow);

    return FollowDto.from(savedFollow);
  }
}
