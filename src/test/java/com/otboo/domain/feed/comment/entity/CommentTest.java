package com.otboo.domain.feed.comment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommentTest {

  @Test
  @DisplayName("댓글 생성 성공")
  void create_success() {
    Feed feed = mock(Feed.class);
    User author = User.create("author@test.com", "author", "password");

    Comment comment = Comment.create(feed, author, "댓글 내용");

    assertThat(comment.getFeed()).isEqualTo(feed);
    assertThat(comment.getAuthor()).isEqualTo(author);
    assertThat(comment.getContent()).isEqualTo("댓글 내용");
  }
}