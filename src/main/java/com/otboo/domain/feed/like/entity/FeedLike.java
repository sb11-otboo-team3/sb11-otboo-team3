package com.otboo.domain.feed.like.entity;

import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.user.entity.User;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "feed_likes")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FeedLike extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "feed_id", nullable = false)
  private Feed feed;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = true)
  private User user;

  private FeedLike(Feed feed, User user) {
    this.feed = feed;
    this.user = user;
  }

  public static FeedLike create(Feed feed, User user) {
    return new FeedLike(feed, user);
  }
}
