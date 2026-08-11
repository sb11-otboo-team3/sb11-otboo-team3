package com.otboo.domain.feed.like.service;

import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.exception.FeedNotFoundException;
import com.otboo.domain.feed.core.exception.FeedUserNotFoundException;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.feed.like.entity.FeedLike;
import com.otboo.domain.feed.like.exception.DuplicateFeedLikeException;
import com.otboo.domain.feed.like.exception.FeedLikeNotFoundException;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedLikeService {

  private final UserRepository userRepository;
  private final FeedRepository feedRepository;
  private final FeedLikeRepository feedLikeRepository;

  @Transactional
  public void createFeedLike(UUID feedId, UUID currentUserId) {
    User user = userRepository.findById(currentUserId)
        .orElseThrow(() -> new FeedUserNotFoundException(currentUserId));

    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    if (!feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId)) {
      FeedLike feedLike = FeedLike.create(feed, user);
      feedLikeRepository.save(feedLike);
      feedRepository.increaseLikeCount(feedId);
    } else {
      throw new DuplicateFeedLikeException();
    }
  }

  @Transactional
  public void deleteFeedLike(UUID feedId, UUID currentUserId) {
    userRepository.findById(currentUserId)
        .orElseThrow(() -> new FeedUserNotFoundException(currentUserId));

    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    FeedLike feedLike = feedLikeRepository.findByFeedIdAndUserId(feedId, currentUserId)
        .orElseThrow(() -> new FeedLikeNotFoundException(feedId));

    feedLikeRepository.delete(feedLike);
    feedRepository.decreaseLikeCount(feedId);
  }
}
