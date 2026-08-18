package com.otboo.domain.feed.like.service;

import com.otboo.domain.feed.core.cache.FeedAuthorListCache;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.exception.FeedNotFoundException;
import com.otboo.domain.feed.core.exception.FeedUserNotFoundException;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.feed.like.entity.FeedLike;
import com.otboo.domain.feed.like.exception.DuplicateFeedLikeException;
import com.otboo.domain.feed.like.exception.FeedLikeNotFoundException;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedLikeService {

  private final UserRepository userRepository;
  private final FeedRepository feedRepository;
  private final FeedLikeRepository feedLikeRepository;
  private final FeedAuthorListCache feedAuthorListCache;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void createFeedLike(UUID feedId, UUID currentUserId) {
    User user = userRepository.findById(currentUserId)
        .orElseThrow(() -> new FeedUserNotFoundException(currentUserId));

    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    try {
      FeedLike feedLike = FeedLike.create(feed, user);
      feedLikeRepository.saveAndFlush(feedLike);
      feedRepository.increaseLikeCount(feedId);
      evictAuthorFeedsAfterCommit(feed.getAuthor().getId());
      if (!feed.getAuthor().getId().equals(currentUserId)) {
        eventPublisher.publishEvent(
            new NotificationEvent(
                feed.getAuthor().getId(),
                "새 좋아요가 등록되었습니다.",
                user.getName() + "님이 회원님의 피드에 좋아요를 눌렀습니다.",
                NotificationLevel.INFO
            )
        );
      }
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateFeedLikeException();
    }
  }

  @Transactional
  public void deleteFeedLike(UUID feedId, UUID currentUserId) {
    userRepository.findById(currentUserId)
        .orElseThrow(() -> new FeedUserNotFoundException(currentUserId));

    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    long deletedCount = feedLikeRepository.deleteByFeedIdAndUserId(feedId, currentUserId);

    if (deletedCount == 0) {
      throw new FeedLikeNotFoundException(feedId);
    }

    feedRepository.decreaseLikeCount(feedId);

    evictAuthorFeedsAfterCommit(feed.getAuthor().getId());
  }

  private void evictAuthorFeedsAfterCommit(UUID authorId) {
    Runnable evict = () -> feedAuthorListCache.evictAuthorFeeds(authorId);

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
