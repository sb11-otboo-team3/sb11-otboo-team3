package com.otboo.domain.feed.clothes.entity;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "feed_clothes")
public class FeedClothes extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "feed_id", nullable = false)
  private Feed feed;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "clothes_id", nullable = false)
  private Clothes clothes;

  private FeedClothes(Feed feed, Clothes clothes) {
    this.feed = feed;
    this.clothes = clothes;
  }

  public static FeedClothes create(Feed feed, Clothes clothes) {
    return new FeedClothes(feed, clothes);
  }
}
