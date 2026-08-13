package com.otboo.domain.feed.comment.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDto;
import com.otboo.domain.feed.comment.entity.Comment;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class FeedCommentMapperTest {

  @Mock
  private UserSummaryMapper userSummaryMapper;

  @InjectMocks
  private FeedCommentMapper feedCommentMapper;

  @Test
  @DisplayName("피드 댓글 DTO 변환 성공")
  void toDto_success() {
    UUID feedId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-13T01:00:00Z");

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        mock(JsonNode.class),
        "피드 내용"
    );
    ReflectionTestUtils.setField(feed, "id", feedId);

    Comment comment = Comment.create(feed, author, "댓글 내용");
    ReflectionTestUtils.setField(comment, "id", commentId);
    ReflectionTestUtils.setField(comment, "createdAt", createdAt);

    UserSummary authorSummary = new UserSummary(authorId, "author", "author-image");
    given(userSummaryMapper.toUserSummary(author)).willReturn(authorSummary);

    FeedCommentDto result = feedCommentMapper.toDto(comment);

    assertThat(result.id()).isEqualTo(commentId);
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.feedId()).isEqualTo(feedId);
    assertThat(result.author()).isEqualTo(authorSummary);
    assertThat(result.content()).isEqualTo("댓글 내용");

    verify(userSummaryMapper).toUserSummary(author);
  }
}
