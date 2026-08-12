package com.otboo.domain.feed.comment.service;

import com.otboo.domain.feed.comment.dto.request.FeedCommentCreateRequest;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDto;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDtoCursorResponse;
import com.otboo.domain.feed.comment.entity.Comment;
import com.otboo.domain.feed.comment.exception.FeedCommentForbiddenException;
import com.otboo.domain.feed.comment.exception.InvalidFeedCommentCursorException;
import com.otboo.domain.feed.comment.exception.InvalidFeedCommentRequestException;
import com.otboo.domain.feed.comment.mapper.FeedCommentMapper;
import com.otboo.domain.feed.comment.repository.FeedCommentRepository;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.exception.FeedNotFoundException;
import com.otboo.domain.feed.core.exception.FeedUserNotFoundException;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedCommentService {

  private final UserRepository userRepository;
  private final FeedRepository feedRepository;
  private final FeedCommentRepository feedCommentRepository;
  private final FeedCommentMapper feedCommentMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public FeedCommentDto createFeedComment(
      UUID feedId,
      FeedCommentCreateRequest request,
      UUID currentUserId
  ) {
    User user = userRepository.findById(currentUserId)
        .orElseThrow(() -> new FeedUserNotFoundException(currentUserId));

    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    if (!feedId.equals(request.feedId())) {
      throw new InvalidFeedCommentRequestException();
    }

    if (!currentUserId.equals(request.authorId())) {
      throw new FeedCommentForbiddenException();
    }

    Comment comment = Comment.create(feed, user, request.content());
    Comment savedComment = feedCommentRepository.save(comment);

    feedRepository.increaseCommentCount(feedId);

    if (!feed.getAuthor().getId().equals(currentUserId)) {
      eventPublisher.publishEvent(
          new NotificationEvent(
              feed.getAuthor().getId(),
              "새 댓글이 등록되었습니다.",
              user.getName() + "님이 회원님의 피드에 댓글을 남겼습니다.",
              NotificationLevel.INFO
          )
      );
    }

    return feedCommentMapper.toDto(savedComment);
  }

  public FeedCommentDtoCursorResponse getComment(
      UUID feedId,
      String cursor,
      UUID idAfter,
      int limit
  ) {
    validateCursor(cursor, idAfter);

    feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    List<Comment> comments = feedCommentRepository.findComments(
        feedId,
        cursor,
        idAfter,
        limit + 1
    );

    boolean hasNext = comments.size() > limit;

    if (hasNext) {
      comments = comments.subList(0, limit);
    }

    List<FeedCommentDto> data = comments.stream()
        .map(feedCommentMapper::toDto)
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Comment last = comments.get(comments.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    long totalCount = feedCommentRepository.countComments(feedId);

    return new FeedCommentDtoCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "ASCENDING"
    );
  }

  // cursor와 idAfter는 둘 다 있거나 둘 다 없어야 함
  private void validateCursor(String cursor, UUID idAfter) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidFeedCommentCursorException();
    }

    if (hasCursor) {
      try {
        Instant.parse(cursor);
      } catch (DateTimeParseException exception) {
        throw new InvalidFeedCommentCursorException();
      }
    }
  }
}
