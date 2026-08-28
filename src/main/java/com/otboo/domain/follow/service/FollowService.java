package com.otboo.domain.follow.service;

import com.otboo.domain.follow.cache.FollowListCache;
import com.otboo.domain.follow.cache.FollowSummaryCache;
import com.otboo.domain.follow.dto.request.FollowCreateRequest;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.follow.dto.response.FollowSummaryDto;
import com.otboo.domain.follow.entity.Follow;
import com.otboo.domain.follow.exception.DuplicateFollowException;
import com.otboo.domain.follow.exception.FollowForbiddenException;
import com.otboo.domain.follow.exception.FollowNotFoundException;
import com.otboo.domain.follow.exception.FollowUserNotFoundException;
import com.otboo.domain.follow.exception.InvalidFollowCursorException;
import com.otboo.domain.follow.exception.SelfFollowNotAllowedException;
import com.otboo.domain.follow.mapper.FollowMapper;
import com.otboo.domain.follow.repository.FollowRepository;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

  private final FollowRepository followRepository;
  private final UserRepository userRepository;
  private final FollowSummaryCache followSummaryCache;
  private final FollowListCache followListCache;
  private final ApplicationEventPublisher eventPublisher;
  private final FollowMapper followMapper;

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

    // 오래된 캐시 삭제(교체 작업)
    evictFollowCacheAfterCommit(follower.getId(), followee.getId());

    eventPublisher.publishEvent(
        new NotificationEvent(
            followee.getId(),
            "새로운 팔로워",
            follower.getName() + "님이 회원님을 팔로우했습니다.",
            NotificationLevel.INFO
        )
    );

    return followMapper.toDto(savedFollow);
  }

  @Transactional
  public void cancelFollow(UUID followId, UUID currentUserId){
    Follow follow = followRepository.findById(followId)
        .orElseThrow(() -> new FollowNotFoundException(followId));

    if(!follow.getFollower().getId().equals(currentUserId)){
      throw new FollowForbiddenException();
    }

    followRepository.delete(follow);

    // 캐시 삭제
    evictFollowCacheAfterCommit(
        follow.getFollower().getId(),
        follow.getFollowee().getId()
    );
  }

  public FollowListResponse getFollowings(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ){
    validateCursor(cursor, idAfter);

    if (!userRepository.existsById(followerId)) {
      throw new FollowUserNotFoundException(followerId);
    }

    Optional<FollowListResponse> cachedResponse = followListCache.findFollowings(
        followerId,
        cursor,
        idAfter,
        limit,
        nameLike
    );

    if (cachedResponse.isPresent()) {
      return cachedResponse.get();
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

    List<FollowDto> data = followMapper.toDtos(follows);
    // 기본은 다음 페이지 없음
    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Follow last = follows.get(follows.size() - 1);
      nextCursor = last.getFollowee().getName().toLowerCase();
      nextIdAfter = last.getId();
    }

    long totalCount = followRepository.countFollowings(followerId, nameLike);

    FollowListResponse response = new FollowListResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "name",
        "ASCENDING"
    );

    followListCache.saveFollowings(
        followerId,
        cursor,
        idAfter,
        limit,
        nameLike,
        response
    );

    return response;
  }

  public FollowListResponse getFollowers(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ){
    validateCursor(cursor, idAfter);

    if (!userRepository.existsById(followeeId)) {
      throw new FollowUserNotFoundException(followeeId);
    }

    Optional<FollowListResponse> cachedResponse = followListCache.findFollowers(
        followeeId,
        cursor,
        idAfter,
        limit,
        nameLike
    );

    if (cachedResponse.isPresent()) {
      return cachedResponse.get();
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

    List<FollowDto> data = followMapper.toDtos(follows);

    // 기본은 다음 페이지 없음
    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Follow last = follows.get(follows.size() - 1);
      nextCursor = last.getFollower().getName().toLowerCase();
      nextIdAfter = last.getId();
    }

    long totalCount = followRepository.countFollowers(followeeId, nameLike);

    FollowListResponse response = new FollowListResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "name",
        "ASCENDING"
    );

    followListCache.saveFollowers(
        followeeId,
        cursor,
        idAfter,
        limit,
        nameLike,
        response
    );

    return response;
  }

  public FollowSummaryDto getFollowSummary(UUID userId, UUID currentUserId){
    // 조회하려는 userId가 존재하는지 여부
    if (!userRepository.existsById(userId)) {
      throw new FollowUserNotFoundException(userId);
    }

    Optional<FollowSummaryDto> cachedSummary = followSummaryCache.find(userId, currentUserId);

    if(cachedSummary.isPresent()){
      return cachedSummary.get();
    }

    Follow followedByMeFollow = followRepository
        .findByFollowerIdAndFolloweeId(currentUserId, userId)
        .orElse(null);

    // currentUserId가 userId를 팔로우 하는지 여부
    boolean followedByMe = followedByMeFollow != null;
    // 만약 currentUserId가 userId를 팔로우중이라면 그때의 팔로우 id
    UUID followedByMeId = followedByMe ? followedByMeFollow.getId() : null;

    boolean followingMe = followRepository.existsByFollowerIdAndFolloweeId(
        userId,
        currentUserId
    );

    FollowSummaryDto summary = new FollowSummaryDto(
        userId,
        followRepository.countFollowers(userId, null),
        followRepository.countFollowings(userId, null),
        followedByMe,
        followedByMeId,
        followingMe
    );

    followSummaryCache.save(userId, currentUserId, summary);

    return summary;
  }

  // cursor와 idAfter는 둘 다 있거나 둘 다 없어야 함
  private void validateCursor(String cursor, UUID idAfter) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidFollowCursorException();
    }
  }

  private void evictFollowCacheAfterCommit(UUID followerId, UUID followeeId) {
    Runnable evict = () -> {
      followSummaryCache.evictRelatedTo(followerId);
      followSummaryCache.evictRelatedTo(followeeId);
      followListCache.evictFollowings(followerId);
      followListCache.evictFollowers(followeeId);
    };

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          evict.run();
        }
      });
      return;
    }

    evict.run();
  }
}
