package com.otboo.domain.follow.service;

import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.follow.exception.DuplicateFollowException;
import com.otboo.domain.follow.exception.FollowForbiddenException;
import com.otboo.domain.follow.exception.FollowNotFoundException;
import com.otboo.domain.follow.exception.FollowUserNotFoundException;
import com.otboo.domain.follow.exception.SelfFollowNotAllowedException;
import com.otboo.domain.follow.repository.FollowRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
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
  public FollowDto createFollow(FollowCreateRequest request, UUID currentUserId) {
    if (!currentUserId.equals(request.followerId())) {
      throw new FollowForbiddenException();
    }

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

  @Transactional
  public void cancelFollow(UUID followId, UUID currentUserId){
    Follow follow = followRepository.findById(followId)
        .orElseThrow(() -> new FollowNotFoundException(followId));

    if(!follow.getFollower().getId().equals(currentUserId)){
      throw new FollowForbiddenException();
    }

    followRepository.delete(follow);
  }

  public FollowListResponse getFollowings(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ){
    if (!userRepository.existsById(followerId)) {
      throw new FollowUserNotFoundException(followerId);
    }

    List<Follow> follows = followRepository.findFollowings(
        followerId,
        cursor,
        idAfter,
        limit + 1,
        nameLike
    );

    boolean hasNext = follows.size() > limit;

    if(hasNext){
      follows = follows.subList(0, limit);
    }

    List<FollowDto> data = follows.stream()
        .map(FollowDto::from)
        .toList();

    // 기본은 다음 페이지 없음
    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Follow last = follows.get(follows.size() - 1);
      nextCursor = last.getFollowee().getName().toLowerCase();
      nextIdAfter = last.getId();
    }

    long totalCount = followRepository.countFollowings(followerId, nameLike);

    return new FollowListResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "name",
        "ASCENDING"
    );
  }

  public FollowListResponse getFollowers(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ){
    if (!userRepository.existsById(followeeId)) {
      throw new FollowUserNotFoundException(followeeId);
    }

    List<Follow> follows = followRepository.findFollowers(
        followeeId,
        cursor,
        idAfter,
        limit + 1,
        nameLike
    );

    boolean hasNext = follows.size() > limit;

    if(hasNext){
      follows = follows.subList(0, limit);
    }

    List<FollowDto> data = follows.stream()
        .map(FollowDto::from)
        .toList();

    // 기본은 다음 페이지 없음
    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Follow last = follows.get(follows.size() - 1);
      nextCursor = last.getFollower().getName().toLowerCase();
      nextIdAfter = last.getId();
    }

    long totalCount = followRepository.countFollowers(followeeId, nameLike);

    return new FollowListResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "name",
        "ASCENDING"
    );
  }
}
