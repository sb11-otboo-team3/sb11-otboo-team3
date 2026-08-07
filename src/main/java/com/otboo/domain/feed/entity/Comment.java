package com.otboo.domain.feed.entity;

import com.otboo.domain.user.entity.User;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "comments")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "feed_id", nullable = false)
  private Feed feed;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", nullable = true)
  private User author;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  private Comment(Feed feed, User author, String content) {
    this.feed = feed;
    this.author = author;
    this.content = content;
  }

  public static Comment create(Feed feed, User user, String content) {
    return new Comment(feed, user, content);
  }
}
