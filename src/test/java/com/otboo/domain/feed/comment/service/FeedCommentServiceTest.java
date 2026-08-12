package com.otboo.domain.feed.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.feed.comment.dto.request.FeedCommentCreateRequest;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDto;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDtoCursorResponse;
import com.otboo.domain.feed.comment.entity.Comment;
import com.otboo.domain.feed.comment.mapper.FeedCommentMapper;
import com.otboo.domain.feed.comment.repository.FeedCommentRepository;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedCommentServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private FeedRepository feedRepository;

  @Mock
  private FeedCommentRepository feedCommentRepository;

  @Mock
  private FeedCommentMapper feedCommentMapper;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private FeedCommentService feedCommentService;
  @Test
  @DisplayName("피드 댓글 생성 성공 테스트")
  void createFeedComment_success() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "댓글 대상 피드"
    );

    FeedCommentDto feedCommentDto = mock(FeedCommentDto.class);

    FeedCommentCreateRequest request = new FeedCommentCreateRequest(
        feedId,
        authorId,
        "댓글 내용"
    );

    given(userRepository.findById(authorId)).willReturn(Optional.of(author));
    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));
    given(feedCommentRepository.save(any(Comment.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(feedCommentMapper.toDto(any(Comment.class))).willReturn(feedCommentDto);

    FeedCommentDto result = feedCommentService.createFeedComment(feedId, request, authorId);

    assertThat(result).isEqualTo(feedCommentDto);
    verify(feedCommentRepository).save(any(Comment.class));
    verify(feedRepository).increaseCommentCount(feedId);
  }



  @Test
  @DisplayName("피드 댓글 목록 조회 성공 테스트")
  void getComment_success() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.valueToTree(mock(WeatherSummaryDto.class)),
        "피드 내용"
    );
    ReflectionTestUtils.setField(feed, "id", feedId);

    Comment comment1 = Comment.create(feed, author, "첫 번째 댓글");
    UUID comment1Id = UUID.randomUUID();
    Instant comment1CreatedAt = Instant.parse("2026-08-11T00:00:00Z");
    ReflectionTestUtils.setField(comment1, "id", comment1Id);
    ReflectionTestUtils.setField(comment1, "createdAt", comment1CreatedAt);

    Comment comment2 = Comment.create(feed, author, "두 번째 댓글");
    ReflectionTestUtils.setField(comment2, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(comment2, "createdAt", Instant.parse("2026-08-11T01:00:00Z"));

    FeedCommentDto feedCommentDto = mock(FeedCommentDto.class);

    given(feedRepository.findByIdAndDeletedAtIsNull(feedId))
        .willReturn(Optional.of(feed));
    given(feedCommentRepository.findComments(feedId, null, null, 2))
        .willReturn(List.of(comment1, comment2));
    given(feedCommentMapper.toDto(comment1))
        .willReturn(feedCommentDto);
    given(feedCommentRepository.countComments(feedId))
        .willReturn(2L);

    FeedCommentDtoCursorResponse result = feedCommentService.getComment(
        feedId,
        null,
        null,
        1
    );

    assertThat(result.data()).containsExactly(feedCommentDto);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(comment1CreatedAt.toString());
    assertThat(result.nextIdAfter()).isEqualTo(comment1Id);
    assertThat(result.totalCount()).isEqualTo(2L);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo("ASCENDING");

    verify(feedRepository).findByIdAndDeletedAtIsNull(feedId);
    verify(feedCommentRepository).findComments(feedId, null, null, 2);
    verify(feedCommentMapper).toDto(comment1);
    verify(feedCommentRepository).countComments(feedId);
  }
}
